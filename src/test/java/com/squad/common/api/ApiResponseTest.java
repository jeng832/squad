package com.squad.common.api;

import com.squad.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void success_데이터만_전달하면_성공_응답_생성() {
        String data = "테스트 데이터";

        ApiResponse<String> response = ApiResponse.success(data);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(data);
        assertThat(response.getMessage()).isNull();
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    void success_데이터와_메시지_전달하면_성공_응답_생성() {
        String data = "테스트 데이터";
        String message = "생성 완료";

        ApiResponse<String> response = ApiResponse.success(data, message);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(data);
        assertThat(response.getMessage()).isEqualTo(message);
        assertThat(response.getErrorCode()).isNull();
    }

    @Test
    void error_ErrorCode만_전달하면_기본_메시지_사용_에러_응답_생성() {
        ErrorCode errorCode = ErrorCode.AGENT_NOT_FOUND;

        ApiResponse<Object> response = ApiResponse.error(errorCode);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo(errorCode.getMessage());
        assertThat(response.getErrorCode()).isEqualTo(errorCode.name());
    }

    @Test
    void error_커스텀_메시지_전달하면_커스텀_메시지_사용_에러_응답_생성() {
        ErrorCode errorCode = ErrorCode.AGENT_NOT_FOUND;
        String customMessage = "ID: 123인 에이전트를 찾을 수 없습니다.";

        ApiResponse<Object> response = ApiResponse.error(errorCode, customMessage);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getMessage()).isEqualTo(customMessage);
        assertThat(response.getErrorCode()).isEqualTo(errorCode.name());
    }
}
