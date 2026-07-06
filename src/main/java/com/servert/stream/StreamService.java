package com.servert.stream;

import com.servert.session.InvalidParameterException;
import com.servert.session.VideoParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 스트림 제어/생명주기 관리(제어 평면). (FR-S-01/03/04, NFR-E-01/02, FR-S-06)
 * <ul>
 *   <li>세션 수에 따라 게시 시작/중지 (첫 접속 시작, 마지막 해제 중지)</li>
 *   <li>화면 제어(해상도/프레임레이트) 적용 → 게시 재시작 (스트림 전역)</li>
 *   <li>감시 스케줄러: "가동해야 하는데 죽음" 감지 시 재시작 (3초 간격, NFR-E-02)</li>
 * </ul>
 */
@Service
public class StreamService {
    private static final Logger log = LoggerFactory.getLogger(StreamService.class);

    private final StreamPublisher publisher;
    private final DeviceProbe deviceProbe;
    private final String mediamtxApiUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build();

    private final AtomicInteger activeClients = new AtomicInteger(0);
    private volatile boolean shouldRun = false;
    private volatile VideoParams current;
    private final Object lock = new Object();

    public StreamService(
            StreamPublisher publisher,
            DeviceProbe deviceProbe,
            @Value("${camera.default-resolution:1920x1080}") String defaultResolution,
            @Value("${camera.default-frame-rate:30}") int defaultFrameRate,
            @Value("${mediamtx.api-url:http://localhost:9997}") String mediamtxApiUrl) {
        this.publisher = publisher;
        this.deviceProbe = deviceProbe;
        this.current = new VideoParams(defaultResolution, defaultFrameRate, "H.264");
        this.mediamtxApiUrl = mediamtxApiUrl;
    }

    /**
     * 카메라 선택(클라이언트가 사용할 카메라 변경). 스트림 전역으로 적용되며, 실행 중이면 재시작한다.
     * 조회 가능한 장치 목록이 있으면 그 안에 속하는지 검증한다(목록을 못 얻으면 통과시키고 경고).
     */
    public StreamStatus selectCamera(String device) {
        if (device == null || device.isBlank()) {
            throw new InvalidParameterException("device must not be blank");
        }
        List<String> available = deviceProbe.listVideoDevices();
        if (!available.isEmpty() && !available.contains(device)) {
            throw new InvalidParameterException("unknown camera device: " + device + " (available: " + available + ")");
        }
        if (available.isEmpty()) {
            log.warn("[STREAM] device list unavailable — accepting '{}' without validation", device);
        }
        synchronized (lock) {
            publisher.selectDevice(device);
            log.info("[STREAM] camera changed to \"{}\"", device);
            if (shouldRun) {
                publisher.stop();
                publisher.start(current);
                log.info("[STREAM] restarted with new camera");
            }
        }
        return status();
    }

    /** 클라이언트 접속 시 호출 — 첫 접속이면 스트림 시작. */
    public void onClientConnected() {
        int n = activeClients.incrementAndGet();
        if (n == 1) {
            log.info("[STREAM] first client connected -> starting stream");
            startInternal();
        }
        log.debug("[STREAM] activeClients={}", n);
    }

    /** 클라이언트 해제 시 호출 — 마지막 해제면 스트림 중지. */
    public void onClientDisconnected() {
        int n = activeClients.decrementAndGet();
        if (n <= 0) {
            activeClients.set(0);
            log.info("[STREAM] last client disconnected -> stopping stream");
            stopInternal();
        }
        log.debug("[STREAM] activeClients={}", Math.max(n, 0));
    }

    /** 화면 제어: 해상도/프레임레이트 적용(스트림 전역). 실행 중이면 재시작. (FR-S-01)
     *  PATCH 의미로 생략(null/0)한 필드는 현재값을 유지한다. codec 은 H.264 고정. */
    public VideoParams applyVideo(VideoParams params) {
        String resolution = (params.resolution() != null && !params.resolution().isBlank())
                ? params.resolution() : current.resolution();
        int frameRate = (params.frameRate() > 0) ? params.frameRate() : current.frameRate();
        VideoParams merged = new VideoParams(resolution, frameRate, "H.264");
        merged.validate();
        synchronized (lock) {
            this.current = merged;
            log.info("[VIDEO] control applied: resolution={} fps={}", merged.resolution(), merged.frameRate());
            if (shouldRun) {
                publisher.stop();
                publisher.start(current);
                log.info("[STREAM] restarted with new params");
            }
        }
        return current;
    }

    /** 관리자용 수동 시작. */
    public void start() {
        startInternal();
    }

    /** 관리자용 수동 중지. */
    public void stop() {
        stopInternal();
    }

    private void startInternal() {
        synchronized (lock) {
            shouldRun = true;
            publisher.start(current);
        }
    }

    private void stopInternal() {
        synchronized (lock) {
            shouldRun = false;
            publisher.stop();
        }
    }

    /**
     * 감시 스케줄러(NFR-E-01/02). 가동해야 하는데 게시가 죽어 있으면 재시작.
     * 간격 = stream.reconnect-interval-ms (기본 3000ms).
     */
    @Scheduled(fixedDelayString = "${stream.reconnect-interval-ms:3000}")
    public void watchdog() {
        if (shouldRun && !publisher.isRunning()) {
            log.warn("[STREAM] publisher not running while expected -> reconnect (interval). lastError={}",
                    publisher.lastError());
            synchronized (lock) {
                if (shouldRun && !publisher.isRunning()) {
                    publisher.start(current);
                }
            }
        }
    }

    public StreamStatus status() {
        return new StreamStatus(
                publisher.isRunning() ? "RUNNING" : "STOPPED",
                publisher.device(),
                current.resolution(),
                current.frameRate(),
                current.codec(),
                activeClients.get(),
                mediamtxReachable(),
                publisher.lastError());
    }

    public int activeClients() {
        return activeClients.get();
    }

    /** MediaMTX API 도달성(best-effort). API 비활성 시 false. */
    private boolean mediamtxReachable() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(mediamtxApiUrl + "/v3/paths/list"))
                    .timeout(Duration.ofMillis(500)).GET().build();
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }
}
