package com.servert.session;

/**
 * 존재하지 않거나 이미 해제된 세션 접근 시 발생. 프로토콜 오류 코드 SESSION_NOT_FOUND(404).
 */
public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String sessionId) {
        super("session not found: " + sessionId);
    }
}
