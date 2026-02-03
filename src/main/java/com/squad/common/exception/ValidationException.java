package com.squad.common.exception;

/**
 * 비즈니스 규칙 유효성 검증에 실패했을 때 발생하는 예외 (HTTP 400).
 */
public class ValidationException extends SquadException {

    public ValidationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ValidationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
