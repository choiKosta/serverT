package com.servert.session;

import com.servert.stream.StreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 클라이언트 세션 관리(FR-S-02). In-memory 저장소.
 * <p>다중 클라이언트 접속을 지원한다(요구사항 §8-1 확정). 다수 세션이 하나의 게시 스트림을
 * 공유하며, 실제 팬아웃은 MediaMTX 가 담당한다. 세션 수 변화에 따라 스트림을 시작/중지한다.
 */
@Service
public class SessionService {
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final StreamService stream;

    // 순환 의존(StreamService 는 SessionService 를 참조하지 않지만, 명시적 @Lazy 로 초기화 순서 안전 확보)
    public SessionService(@Lazy StreamService stream) {
        this.stream = stream;
    }

    /** 접속: 세션 생성. 첫 접속이면 스트림을 시작한다. (FR-S-02, FR-S-03/04) */
    public Session connect(String clientName, VideoParams video) {
        String id = UUID.randomUUID().toString();
        VideoParams v = (video != null) ? video : VideoParams.defaults();
        Session session = new Session(id, clientName, v, Instant.now());
        sessions.put(id, session);
        log.info("[CONNECT] session created: id={} client={} active={}", id, clientName, sessions.size());
        stream.onClientConnected();
        return session;
    }

    /** 세션 조회. 없으면 예외. */
    public Session get(String sessionId) {
        Session s = sessions.get(sessionId);
        if (s == null) {
            log.warn("[LOOKUP] session not found: id={}", sessionId);
            throw new SessionNotFoundException(sessionId);
        }
        return s;
    }

    /** 접속 해제: 세션 제거. (FR-S-02) */
    public void disconnect(String sessionId) {
        Session removed = sessions.remove(sessionId);
        if (removed == null) {
            log.warn("[DISCONNECT] session not found: id={}", sessionId);
            throw new SessionNotFoundException(sessionId);
        }
        log.info("[DISCONNECT] session closed: id={} active={}", sessionId, sessions.size());
        stream.onClientDisconnected();
    }

    public int activeCount() {
        return sessions.size();
    }

    /** RTSP 접속 URL(계약 고정: 8554/camera, NFR-I-01). */
    public String rtspUrl() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            host = "localhost";
        }
        return "rtsp://" + host + ":8554/camera";
    }
}
