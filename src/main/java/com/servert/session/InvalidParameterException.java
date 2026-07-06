package com.servert.session;

/**
 * 잘못된 요청 파라미터(미지원 해상도/프레임레이트 등). 프로토콜 오류 코드 INVALID_PARAMETER(400).
 */
public class InvalidParameterException extends RuntimeException {
    public InvalidParameterException(String message) {
        super(message);
    }
}
