package com.squad.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SquadExceptionTest {

    @Test
    void SquadException_ErrorCode만_생성시_기본_메시지_사용() {
        SquadException exception = new SquadException(ErrorCode.AGENT_NOT_FOUND);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AGENT_NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo(ErrorCode.AGENT_NOT_FOUND.getMessage());
    }

    @Test
    void SquadException_커스텀_메시지_생성시_커스텀_메시지_사용() {
        String customMessage = "커스텀 에러 메시지";

        SquadException exception = new SquadException(ErrorCode.AGENT_NOT_FOUND, customMessage);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AGENT_NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo(customMessage);
    }

    @Test
    void NotFoundException_은_SquadException_하위_클래스() {
        NotFoundException exception = new NotFoundException(ErrorCode.SQUAD_NOT_FOUND);

        assertThat(exception).isInstanceOf(SquadException.class);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SQUAD_NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo(ErrorCode.SQUAD_NOT_FOUND.getMessage());
    }

    @Test
    void ValidationException_은_SquadException_하위_클래스() {
        ValidationException exception = new ValidationException(ErrorCode.VALIDATION_FAILED, "테스트 메시지");

        assertThat(exception).isInstanceOf(SquadException.class);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(exception.getMessage()).isEqualTo("테스트 메시지");
    }
}
