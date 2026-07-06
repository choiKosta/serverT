package com.servert.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 스트림 상태 조회/관리 REST. 인터페이스 프로토콜 확장.
 */
@RestController
@RequestMapping("/api/v1/stream")
public class StreamController {
    private static final Logger log = LoggerFactory.getLogger(StreamController.class);

    private final StreamService stream;
    private final DeviceProbe deviceProbe;

    public StreamController(StreamService stream, DeviceProbe deviceProbe) {
        this.stream = stream;
        this.deviceProbe = deviceProbe;
    }

    /** 스트림 상태/오류/클라이언트 수. GET /api/v1/stream/status (FR-S-06) */
    @GetMapping("/status")
    public StreamStatus status() {
        return stream.status();
    }

    /** 관리자용 수동 시작. POST /api/v1/stream/start */
    @PostMapping("/start")
    public StreamStatus start() {
        log.info("[STREAM] manual start requested");
        stream.start();
        return stream.status();
    }

    /** 관리자용 수동 중지. POST /api/v1/stream/stop */
    @PostMapping("/stop")
    public StreamStatus stop() {
        log.info("[STREAM] manual stop requested");
        stream.stop();
        return stream.status();
    }

    /** 로컬 웹캠 목록. GET /api/v1/stream/devices (클라이언트가 선택할 후보) */
    @GetMapping("/devices")
    public Map<String, List<String>> devices() {
        return Map.of("videoDevices", deviceProbe.listVideoDevices());
    }

    /** 카메라 선택. PUT /api/v1/stream/camera  body: {"device":"..."}
     *  스트림 전역 적용(모든 클라이언트 공유). 실행 중이면 새 카메라로 재시작. */
    @PutMapping("/camera")
    public StreamStatus selectCamera(@RequestBody CameraSelection request) {
        String device = (request != null) ? request.device() : null;
        log.info("[STREAM] camera selection requested: device=\"{}\"", device);
        return stream.selectCamera(device); // 미지원 장치면 400 INVALID_PARAMETER
    }

    public record CameraSelection(String device) {
    }
}
