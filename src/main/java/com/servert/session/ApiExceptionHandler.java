package com.servert.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * 오류 응답을 프로토콜 정의(docs/interface-protocol.md §3.2) 형식으로 변환한다.
 * 형식: { "error": { "code", "message", "timestamp" } }  (FR-S-06 오류 전파)
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(SessionNotFoundException ex) {
        log.warn("[ERROR] 404 SESSION_NOT_FOUND - {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidParameter(InvalidParameterException ex) {
        log.warn("[ERROR] 400 INVALID_PARAMETER - {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String code, String message) {
        Map<String, Object> error = Map.of(
                "code", code,
                "message", message,
                "timestamp", Instant.now().toString()
        );
        return ResponseEntity.status(status).body(Map.of("error", error));
    }
}
