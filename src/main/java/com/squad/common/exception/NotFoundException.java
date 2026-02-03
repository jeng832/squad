package com.squad.common.exception;

/**
 * 요청한 리소스를 찾을 수 없을 때 발생하는 예외 (HTTP 404).
 */
public class NotFoundException extends SquadException {

    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
