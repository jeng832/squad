package com.squad.session.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import com.squad.common.exception.ValidationException;
import com.squad.session.domain.SessionStatus;
import com.squad.session.dto.SessionCreateRequest;
import com.squad.session.dto.SessionResponse;
import com.squad.session.service.SessionExecutionService;
import com.squad.session.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private SessionExecutionService sessionExecutionService;

    private static final LocalDateTime NOW = LocalDateTime.now();

    private SessionResponse sampleResponse(SessionStatus status) {
        return new SessionResponse(
                1L,
                2L,
                "사용자 프롬프트",
                status,
                "작업 결과",
                NOW,
                status == SessionStatus.PENDING ? null : NOW,
                NOW
        );
    }

    @Test
    void 세션_목록_조회_시_성공_응답_반환() throws Exception {
        given(sessionService.findAll()).willReturn(List.of(sampleResponse(SessionStatus.PENDING)));

        mockMvc.perform(get("/api/v1/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
    }

    @Test
    void 세션_ID로_조회_시_성공_응답_반환() throws Exception {
        given(sessionService.findById(1L)).willReturn(sampleResponse(SessionStatus.RUNNING));

        mockMvc.perform(get("/api/v1/sessions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
    }

    @Test
    void 존재하지_않는_세션_조회_시_404_응답() throws Exception {
        given(sessionService.findById(99L)).willThrow(new NotFoundException(ErrorCode.SESSION_NOT_FOUND));

        mockMvc.perform(get("/api/v1/sessions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));
    }

    @Test
    void 세션_생성_시_201_응답_반환() throws Exception {
        SessionCreateRequest request = new SessionCreateRequest(2L, "새 프롬프트");
        SessionResponse response = new SessionResponse(
                10L,
                2L,
                request.userPrompt(),
                SessionStatus.PENDING,
                null,
                null,
                null,
                NOW
        );
        given(sessionService.create(any(SessionCreateRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.message").value("세션이 생성되었습니다."));
    }

    @Test
    void 세션_생성_시_squadId_null_이면_400_응답() throws Exception {
        Map<String, Object> body = Map.of("userPrompt", "프롬프트만 있음");

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void 세션_생성_시_userPrompt_빈값이면_400_응답() throws Exception {
        SessionCreateRequest request = new SessionCreateRequest(1L, " ");

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void 존재하지_않는_Squad로_세션_생성_시_404_응답() throws Exception {
        SessionCreateRequest request = new SessionCreateRequest(99L, "prompt");
        given(sessionService.create(any(SessionCreateRequest.class)))
                .willThrow(new NotFoundException(ErrorCode.SQUAD_NOT_FOUND));

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SQUAD_NOT_FOUND"));
    }

    @Test
    void 세션_시작_시_성공_응답_반환() throws Exception {
        SessionResponse response = sampleResponse(SessionStatus.RUNNING);
        given(sessionExecutionService.start(1L)).willReturn(response);

        mockMvc.perform(post("/api/v1/sessions/1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.message").value("세션이 시작되었습니다."));
    }

    @Test
    void 존재하지_않는_세션_시작_시_404_응답() throws Exception {
        given(sessionExecutionService.start(99L))
                .willThrow(new NotFoundException(ErrorCode.SESSION_NOT_FOUND));

        mockMvc.perform(post("/api/v1/sessions/99/start"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));
    }

    @Test
    void PENDING이_아닌_세션_시작_시_400_응답() throws Exception {
        given(sessionExecutionService.start(1L))
                .willThrow(new ValidationException(ErrorCode.INVALID_SESSION_STATE, "PENDING 상태만 시작 가능"));

        mockMvc.perform(post("/api/v1/sessions/1/start"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_SESSION_STATE"));
    }

    @Test
    void 세션_취소_시_성공_응답_반환() throws Exception {
        SessionResponse response = sampleResponse(SessionStatus.CANCELLED);
        given(sessionService.cancel(1L)).willReturn(response);

        mockMvc.perform(post("/api/v1/sessions/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.message").value("세션이 취소되었습니다."));
    }

    @Test
    void 존재하지_않는_세션_취소_시_404_응답() throws Exception {
        willThrow(new NotFoundException(ErrorCode.SESSION_NOT_FOUND)).given(sessionService).cancel(99L);

        mockMvc.perform(post("/api/v1/sessions/99/cancel"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));
    }

    @Test
    void 완료된_세션_취소_시_400_응답() throws Exception {
        willThrow(new ValidationException(ErrorCode.INVALID_SESSION_STATE, "완료된 세션은 취소할 수 없습니다."))
                .given(sessionService).cancel(1L);

        mockMvc.perform(post("/api/v1/sessions/1/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_SESSION_STATE"));
    }
}
