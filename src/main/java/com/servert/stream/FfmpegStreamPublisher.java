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
        log.info("[STREAM] start: device=\"{}\" params={}x{}@{} target={}",
                device, params.width(), params.height(), params.frameRate(), rtspTarget);
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
        log.info("[STREAM] camera selected: device=\"{}\"", device);
    }

    @Override
    public String device() {
        return device;
    }

    /** ffmpeg dshow 캡처 → libx264 zerolatency → RTSP(TCP) 게시 커맨드. */
    private List<String> buildCommand(VideoParams params) {
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpeg);
        cmd.add("-hide_banner");
        cmd.add("-f");
        cmd.add("dshow");
        cmd.add("-rtbufsize");
        cmd.add("100M");
        cmd.add("-framerate");
        cmd.add(String.valueOf(params.frameRate()));
        cmd.add("-video_size");
        cmd.add(params.width() + "x" + params.height());
        cmd.add("-i");
        cmd.add("video=" + device);
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
