package com.servert.session;

import java.time.Instant;

/**
 * 클라이언트 접속 세션. 인터페이스 프로토콜(docs/interface-protocol.md) §2 세션 모델.
 */
public record Session(String sessionId, String clientName, VideoParams video, Instant createdAt) {
}
