package com.squad.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squad.agent.domain.RoleType;
import com.squad.agent.dto.AgentCreateRequest;
import com.squad.agent.dto.AgentResponse;
import com.squad.agent.dto.AgentUpdateRequest;
import com.squad.agent.service.AgentService;
import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AgentService agentService;

    private static final LocalDateTime NOW = LocalDateTime.now();

    private AgentResponse sampleResponse() {
        return new AgentResponse(
                1L,
                "orch-001",
                RoleType.ORCHESTRATOR,
                "작업을 분석하고 에이전트에게 위임하는 역할",
                Map.of("provider", "claude", "model", "claude-sonnet-4-20250514"),
                NOW,
                NOW
        );
    }

    @Test
    void 에이전트_목록_조회_시_성공_응답_반환() throws Exception {
        given(agentService.findAll()).willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("orch-001"))
                .andExpect(jsonPath("$.data[0].roleType").value("ORCHESTRATOR"));
    }

    @Test
    void 에이전트_ID로_조회_시_성공_응답_반환() throws Exception {
        given(agentService.findById(1L)).willReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/agents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("orch-001"));
    }

    @Test
    void 존재하지_않는_에이전트_조회_시_404_응답() throws Exception {
        given(agentService.findById(99L)).willThrow(new NotFoundException(ErrorCode.AGENT_NOT_FOUND));

        mockMvc.perform(get("/api/v1/agents/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AGENT_NOT_FOUND"));
    }

    @Test
    void 에이전트_생성_시_201_응답_반환() throws Exception {
        AgentCreateRequest request = new AgentCreateRequest(
                "worker-001",
                RoleType.WORKER,
                "주어진 작업을 실행하는 역할",
                Map.of("provider", "claude", "model", "claude-sonnet-4-20250514")
        );
        AgentResponse response = new AgentResponse(
                2L, request.name(), request.roleType(), request.role(), request.llmConfig(), NOW, NOW
        );
        given(agentService.create(any(AgentCreateRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.message").value("에이전트가 생성되었습니다."));
    }

    @Test
    void 에이전트_생성_시_name_빈_값_400_응답() throws Exception {
        AgentCreateRequest request = new AgentCreateRequest(
                "",
                RoleType.WORKER,
                "역할 설명",
                Map.of("provider", "claude")
        );

        mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void 에이전트_생성_시_roleType_null_400_응답() throws Exception {
        // roleType을 null로 설정하기 위해 raw map 사용
        Map<String, Object> body = Map.of(
                "name", "test",
                "role", "역할",
                "llmConfig", Map.of("provider", "claude")
        );

        mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void 에이전트_수정_시_성공_응답_반환() throws Exception {
        AgentUpdateRequest request = new AgentUpdateRequest(
                "orch-001-updated",
                "수정된 역할 설명",
                Map.of("provider", "claude", "model", "claude-opus-4-5-20251101")
        );
        AgentResponse response = new AgentResponse(
                1L, request.name(), RoleType.ORCHESTRATOR, request.role(), request.llmConfig(), NOW, NOW
        );
        given(agentService.update(eq(1L), any(AgentUpdateRequest.class))).willReturn(response);

        mockMvc.perform(put("/api/v1/agents/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("orch-001-updated"));
    }

    @Test
    void 존재하지_않는_에이전트_수정_시_404_응답() throws Exception {
        AgentUpdateRequest request = new AgentUpdateRequest(
                "name",
                "role",
                Map.of("provider", "claude")
        );
        given(agentService.update(eq(99L), any(AgentUpdateRequest.class)))
                .willThrow(new NotFoundException(ErrorCode.AGENT_NOT_FOUND));

        mockMvc.perform(put("/api/v1/agents/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AGENT_NOT_FOUND"));
    }

    @Test
    void 에이전트_삭제_시_성공_응답_반환() throws Exception {
        willDoNothing().given(agentService).delete(1L);

        mockMvc.perform(delete("/api/v1/agents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("에이전트가 삭제되었습니다."));
    }

    @Test
    void 존재하지_않는_에이전트_삭제_시_404_응답() throws Exception {
        willThrow(new NotFoundException(ErrorCode.AGENT_NOT_FOUND)).given(agentService).delete(99L);

        mockMvc.perform(delete("/api/v1/agents/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AGENT_NOT_FOUND"));
    }
}
