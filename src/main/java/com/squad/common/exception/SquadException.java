package com.squad.common.exception;

/**
 * Squad 애플리케이션의 기본 RuntimeException.
 *
 * <p>모든 비즈니스 예외는 이 클래스를 상속받아야 합니다.</p>
 */
public class SquadException extends RuntimeException {

    private final ErrorCode errorCode;

    public SquadException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public SquadException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
