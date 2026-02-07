package com.squad.session.controller;

import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import com.squad.session.domain.MessageType;
import com.squad.session.dto.MessageResponse;
import com.squad.session.service.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessageController.class)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessageService messageService;

    private static final LocalDateTime NOW = LocalDateTime.now();

    private MessageResponse message(Long id, String content, MessageType type) {
        return new MessageResponse(id, 1L, 10L, 20L, content, type, NOW.plusSeconds(id));
    }

    @Test
    void 세션_메시지_목록_조회_시_성공_응답_반환() throws Exception {
        given(messageService.findBySession(eq(1L), any())).willReturn(List.of(
                message(1L, "첫 번째", MessageType.TASK_REQUEST),
                message(2L, "두 번째", MessageType.TASK_RESULT)
        ));

        mockMvc.perform(get("/api/v1/sessions/1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].content").value("첫 번째"))
                .andExpect(jsonPath("$.data[1].type").value("TASK_RESULT"));
    }

    @Test
    void 세션_메시지_타입별_조회_시_성공_응답_반환() throws Exception {
        given(messageService.findBySession(1L, MessageType.TASK_REQUEST)).willReturn(List.of(
                message(1L, "요청1", MessageType.TASK_REQUEST),
                message(2L, "요청2", MessageType.TASK_REQUEST)
        ));

        mockMvc.perform(get("/api/v1/sessions/1/messages")
                        .param("type", "TASK_REQUEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].type").value("TASK_REQUEST"))
                .andExpect(jsonPath("$.data[1].type").value("TASK_REQUEST"));
    }

    @Test
    void 존재하지_않는_세션_메시지_조회_시_404_응답() throws Exception {
        willThrow(new NotFoundException(ErrorCode.SESSION_NOT_FOUND))
                .given(messageService).findBySession(eq(99L), any());

        mockMvc.perform(get("/api/v1/sessions/99/messages"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));
    }
}
