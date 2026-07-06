package com.servert.stream;

import com.servert.session.VideoParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * ffmpeg 자식 프로세스로 로컬 웹캠(Windows dshow)을 캡처→H.264 인코딩→RTSP 게시.
 * 게시된 스트림은 MediaMTX 가 다중 클라이언트에 팬아웃한다. (FR-S-03/04)
 * camera.enabled=true 일 때만 활성화된다(테스트/무카메라 환경은 NoopStreamPublisher).
 */
@Component
@ConditionalOnProperty(name = "camera.enabled", havingValue = "true")
public class FfmpegStreamPublisher implements StreamPublisher {
    private static final Logger log = LoggerFactory.getLogger(FfmpegStreamPublisher.class);

    private final String ffmpeg;
    private final String rtspTarget;

    private volatile String device;
    private volatile Process process;
    private volatile String lastError;
    // 입력에 해상도/프레임레이트 강제가 실패하면 true 로 전환 → 입력 강제를 풀고 출력에서 스케일링.
    private volatile boolean useOutputScaling = false;

    public FfmpegStreamPublisher(
            @Value("${camera.ffmpeg:ffmpeg}") String ffmpeg,
            @Value("${camera.device:}") String device,
            @Value("${stream.rtsp-target:rtsp://localhost:8554/camera}") String rtspTarget) {
        this.ffmpeg = ffmpeg;
        this.device = device;
        this.rtspTarget = rtspTarget;
    }

    @Override
    public synchronized void start(VideoParams params) {
        if (isRunning()) {
            log.debug("[STREAM] already running, ignore start");
            return;
        }
        if (device == null || device.isBlank()) {
            lastError = "camera.device not configured (run GET /api/v1/stream/devices to list webcams)";
            log.warn("[STREAM] cannot start: {}", lastError);
            return;
        }
        List<String> cmd = buildCommand(params);
        log.info("[STREAM] start: device=\"{}\" params={}x{}@{} mode={} target={}",
                device, params.width(), params.height(), params.frameRate(),
                useOutputScaling ? "output-scale" : "input-native", rtspTarget);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            this.process = pb.start();
            this.lastError = null;
            pumpLogs(this.process);
        } catch (IOException e) {
            lastError = "ffmpeg start failed: " + e.getMessage();
            log.error("[STREAM] {}", lastError);
        }
    }

    @Override
    public synchronized void stop() {
        Process p = this.process;
        if (p != null && p.isAlive()) {
            log.info("[STREAM] stop");
            p.destroy();
            try {
                if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        this.process = null;
    }

    @Override
    public boolean isRunning() {
        Process p = this.process;
        return p != null && p.isAlive();
    }

    @Override
    public String lastError() {
        return lastError;
    }

    @Override
    public void selectDevice(String device) {
        this.device = device;
        this.useOutputScaling = false; // 새 카메라는 우선 네이티브 입력으로 시도
        log.info("[STREAM] camera selected: device=\"{}\"", device);
    }

    @Override
    public String device() {
        return device;
    }

    /**
     * ffmpeg dshow 캡처 → libx264 zerolatency → RTSP(TCP) 게시 커맨드.
     * <p>기본은 입력에 해상도/프레임레이트를 강제(카메라 네이티브 지원 시 최적). 미지원 카메라에서
     * 실패하면 {@code useOutputScaling}=true 로 전환되어, 입력 강제를 빼고 출력에서 scale/fps 를 맞춘다.
     */
    private List<String> buildCommand(VideoParams params) {
        String size = params.width() + "x" + params.height();
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpeg);
        cmd.add("-hide_banner");
        cmd.add("-f");
        cmd.add("dshow");
        cmd.add("-rtbufsize");
        cmd.add("100M");
        if (!useOutputScaling) {
            // 입력 네이티브 강제
            cmd.add("-framerate");
            cmd.add(String.valueOf(params.frameRate()));
            cmd.add("-video_size");
            cmd.add(size);
        }
        cmd.add("-i");
        cmd.add("video=" + device);
        if (useOutputScaling) {
            // 카메라 네이티브 입력을 출력에서 원하는 해상도/프레임레이트로 변환
            cmd.add("-vf");
            cmd.add("scale=" + params.width() + ":" + params.height());
            cmd.add("-r");
            cmd.add(String.valueOf(params.frameRate()));
        }
        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add("ultrafast");
        cmd.add("-tune");
        cmd.add("zerolatency");
        cmd.add("-f");
        cmd.add("rtsp");
        cmd.add("-rtsp_transport");
        cmd.add("tcp");
        cmd.add(rtspTarget);
        return cmd;
    }

    /** ffmpeg 출력(stderr 병합)을 로그로 흘려보낸다. 오류 라인은 lastError 로 보관. */
    private void pumpLogs(Process p) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    // 입력 열기 실패(해상도/프레임레이트 미지원 등) → 다음 재시도부터 출력 스케일링으로 전환
                    if (!useOutputScaling &&
                            (line.contains("Could not set video options") || line.contains("Error opening input"))) {
                        useOutputScaling = true;
                        log.warn("[STREAM] input format not supported by device — falling back to output scaling on next retry");
                    }
                    if (line.toLowerCase().contains("error") || line.contains("Could not")) {
                        lastError = line;
                        log.warn("[STREAM][ffmpeg] {}", line);
                    } else {
                        log.debug("[STREAM][ffmpeg] {}", line);
                    }
                }
            } catch (IOException ignored) {
                // 프로세스 종료 시 스트림 닫힘 — 정상
            }
        }, "ffmpeg-log-pump");
        t.setDaemon(true);
        t.start();
    }
}
