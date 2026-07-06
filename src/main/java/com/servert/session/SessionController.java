package com.servert.session;

import com.servert.stream.StreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 클라이언트 접속 Control 채널(REST). 인터페이스 프로토콜 docs/interface-protocol.md §4.
 * 접속(연결) -> 데이터 전송(echo) -> 접속 해제 흐름을 처리하고 각 단계를 로그로 표시한다.
 * (FR-S-01 제어 명령 수신/수행, FR-S-02 접속/해제 관리)
 */
@RestController
@RequestMapping("/api/v1/session")
public class SessionController {
    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private final SessionService sessions;
    private final StreamService stream;

    public SessionController(SessionService sessions, StreamService stream) {
        this.sessions = sessions;
        this.stream = stream;
    }

    /** (1) 접속 요청 -> 세션 생성. POST /api/v1/session */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectResponse connect(@RequestBody(required = false) ConnectRequest request) {
        String clientName = (request != null && request.clientName() != null) ? request.clientName() : "unknown";
        VideoParams video = (request != null) ? request.video() : null;
        log.info("[CONNECT] connect requested: client={}", clientName);

        Session s = sessions.connect(clientName, video);
        return new ConnectResponse(s.sessionId(), sessions.rtspUrl(), s.video(), s.createdAt().toString());
    }

    /** 상태 조회. GET /api/v1/session/{id} */
    @GetMapping("/{sessionId}")
    public Session status(@PathVariable String sessionId) {
        log.debug("[STATUS] status requested: id={}", sessionId);
        return sessions.get(sessionId);
    }

    /** 화면 제어: FrameRate/Resolution 변경. PATCH /api/v1/session/{id}/video (FR-S-01, FR-C-02)
     *  카메라 1대 → 스트림 1개 공유이므로 변경은 전 클라이언트 공통 적용(스트림 전역). */
    @PatchMapping("/{sessionId}/video")
    public VideoParams updateVideo(@PathVariable String sessionId, @RequestBody VideoParams request) {
        sessions.get(sessionId); // 세션 유효성 확인(없으면 404)
        log.info("[VIDEO] control requested: id={} resolution={} fps={}",
                sessionId, request.resolution(), request.frameRate());
        return stream.applyVideo(request); // 검증 실패 시 400 INVALID_PARAMETER
    }

    /** (2) 데이터 전송: 수신 문자열 + " World" 반환. POST /api/v1/session/{id}/echo */
    @PostMapping("/{sessionId}/echo")
    public EchoResponse echo(@PathVariable String sessionId, @RequestBody EchoRequest request) {
        sessions.get(sessionId); // 세션 유효성 확인(없으면 404)
        String received = (request != null && request.message() != null) ? request.message() : "";
        String reply = received + " World";
        log.info("[DATA] echo: id={} received=\"{}\" reply=\"{}\"", sessionId, received, reply);
        return new EchoResponse(reply);
    }

    /** (3) 접속 해제. DELETE /api/v1/session/{id} */
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@PathVariable String sessionId) {
        log.info("[DISCONNECT] disconnect requested: id={}", sessionId);
        sessions.disconnect(sessionId);
    }

    // ---- 요청/응답 DTO (프로토콜 §4) ----
    public record ConnectRequest(String clientName, VideoParams video) {
    }

    public record ConnectResponse(String sessionId, String rtspUrl, VideoParams video, String createdAt) {
    }

    public record EchoRequest(String message) {
    }

    public record EchoResponse(String message) {
    }
}
