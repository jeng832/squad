package com.squad.common.api;

import com.squad.common.exception.ErrorCode;
import lombok.Getter;

/**
 * API 응답 형식을 정의하는 공통 래퍼 클래스.
 *
 * <p>모든 REST API 응답은 이 클래스로 감싸져 일관된 형식을 제공합니다.</p>
 *
 * @param <T> 응답 데이터의 타입
 */
@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final String message;
    private final String errorCode;

    private ApiResponse(boolean success, T data, String message, String errorCode) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.errorCode = errorCode;
    }

    /**
     * 성공 응답을 생성합니다. (데이터만 포함)
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    /**
     * 성공 응답을 생성합니다. (데이터 + 메시지)
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, null);
    }

    /**
     * 에러 응답을 생성합니다. (ErrorCode 기본 메시지 사용)
     */
    public static ApiResponse<Object> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, null, errorCode.getMessage(), errorCode.name());
    }

    /**
     * 에러 응답을 생성합니다. (커스텀 메시지 사용)
     */
    public static ApiResponse<Object> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, null, message, errorCode.name());
    }
}
