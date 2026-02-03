package com.squad.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void SquadException_발생시_해당_에러코드_상태로_응답() throws Exception {
        mockMvc.perform(get("/test/squad-exception"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AGENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(ErrorCode.AGENT_NOT_FOUND.getMessage()));
    }

    @Test
    void NotFoundException_발생시_404_응답_및_커스텀_메시지_반환() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AGENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("테스트 에이전트 미발견"));
    }

    @Test
    void ValidationException_발생시_400_응답() throws Exception {
        mockMvc.perform(get("/test/validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ORCHESTRATOR_REQUIRED"));
    }

    @Test
    void 일반_Exception_발생시_500_응답() throws Exception {
        mockMvc.perform(get("/test/exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    void 지원하지_않는_HTTP_메서드_사용시_405_응답() throws Exception {
        mockMvc.perform(delete("/test/squad-exception"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    /**
     * 테스트용 컨트롤러. GlobalExceptionHandler의 각 핸들러를 트리거하기 위한 엔드포인트를 제공합니다.
     */
    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/squad-exception")
        public void throwSquadException() {
            throw new SquadException(ErrorCode.AGENT_NOT_FOUND);
        }

        @GetMapping("/not-found")
        public void throwNotFoundException() {
            throw new NotFoundException(ErrorCode.AGENT_NOT_FOUND, "테스트 에이전트 미발견");
        }

        @GetMapping("/validation")
        public void throwValidationException() {
            throw new ValidationException(ErrorCode.ORCHESTRATOR_REQUIRED);
        }

        @GetMapping("/exception")
        public void throwException() throws Exception {
            throw new Exception("일반 예외");
        }
    }
}
