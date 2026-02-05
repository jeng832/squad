package com.squad.squad.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import com.squad.common.exception.ValidationException;
import com.squad.squad.dto.SquadCreateRequest;
import com.squad.squad.dto.SquadResponse;
import com.squad.squad.dto.SquadUpdateRequest;
import com.squad.squad.service.SquadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SquadController.class)
class SquadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SquadService squadService;

    private static final LocalDateTime NOW = LocalDateTime.now();

    private SquadResponse sampleResponse() {
        return new SquadResponse(
                1L,
                "dev-squad",
                "개발 팀 Squad",
                100L,
                Set.of(101L, 102L),
                Map.of("allowed", List.of("101-102")),
                NOW,
                NOW
        );
    }

    @Test
    void Squad_목록_조회_시_성공_응답_반환() throws Exception {
        given(squadService.findAll()).willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/squads"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("dev-squad"))
                .andExpect(jsonPath("$.data[0].orchestratorId").value(100));
    }

    @Test
    void Squad_ID로_조회_시_성공_응답_반환() throws Exception {
        given(squadService.findById(1L)).willReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/squads/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.orchestratorId").value(100));
    }

    @Test
    void 존재하지_않는_Squad_조회_시_404_응답() throws Exception {
        given(squadService.findById(99L)).willThrow(new NotFoundException(ErrorCode.SQUAD_NOT_FOUND));

        mockMvc.perform(get("/api/v1/squads/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SQUAD_NOT_FOUND"));
    }

    @Test
    void Squad_생성_시_201_응답_반환() throws Exception {
        SquadCreateRequest request = new SquadCreateRequest(
                "dev-squad",
                "개발 팀 Squad",
                100L,
                List.of(101L, 102L),
                Map.of("allowed", List.of("101-102"))
        );
        given(squadService.create(any(SquadCreateRequest.class))).willReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/squads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.message").value("Squad가 생성되었습니다."));
    }

    @Test
    void Squad_생성_시_orchestratorId_null_400_응답() throws Exception {
        // orchestratorId를 빠지게 하기 위해 raw map 사용
        Map<String, Object> body = Map.of(
                "name", "test-squad"
        );

        mockMvc.perform(post("/api/v1/squads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void Squad_생성_시_orchestrator_존재하지_않으면_404_응답() throws Exception {
        SquadCreateRequest request = new SquadCreateRequest(
                "dev-squad", null, 999L, null, null
        );
        given(squadService.create(any(SquadCreateRequest.class)))
                .willThrow(new NotFoundException(ErrorCode.AGENT_NOT_FOUND));

        mockMvc.perform(post("/api/v1/squads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("AGENT_NOT_FOUND"));
    }

    @Test
    void Squad_생성_시_orchestrator_roleType_불일치_400_응답() throws Exception {
        SquadCreateRequest request = new SquadCreateRequest(
                "dev-squad", null, 101L, null, null
        );
        given(squadService.create(any(SquadCreateRequest.class)))
                .willThrow(new ValidationException(ErrorCode.INVALID_ORCHESTRATOR_ROLE));

        mockMvc.perform(post("/api/v1/squads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("INVALID_ORCHESTRATOR_ROLE"));
    }

    @Test
    void Squad_생성_시_agentIds_없이_성공() throws Exception {
        SquadCreateRequest request = new SquadCreateRequest(
                "solo-squad", null, 100L, null, null
        );
        SquadResponse response = new SquadResponse(2L, "solo-squad", null, 100L, Set.of(), null, NOW, NOW);
        given(squadService.create(any(SquadCreateRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/squads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.agentIds").isEmpty());
    }

    @Test
    void Squad_수정_시_성공_응답_반환() throws Exception {
        SquadUpdateRequest request = new SquadUpdateRequest(
                "dev-squad-v2",
                "수정된 Squad",
                List.of(101L),
                null
        );
        SquadResponse response = new SquadResponse(1L, "dev-squad-v2", "수정된 Squad", 100L, Set.of(101L), null, NOW, NOW);
        given(squadService.update(eq(1L), any(SquadUpdateRequest.class))).willReturn(response);

        mockMvc.perform(put("/api/v1/squads/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("dev-squad-v2"));
    }

    @Test
    void 존재하지_않는_Squad_수정_시_404_응답() throws Exception {
        SquadUpdateRequest request = new SquadUpdateRequest("name", null, null, null);
        given(squadService.update(eq(99L), any(SquadUpdateRequest.class)))
                .willThrow(new NotFoundException(ErrorCode.SQUAD_NOT_FOUND));

        mockMvc.perform(put("/api/v1/squads/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SQUAD_NOT_FOUND"));
    }

    @Test
    void Squad_삭제_시_성공_응답_반환() throws Exception {
        willDoNothing().given(squadService).delete(1L);

        mockMvc.perform(delete("/api/v1/squads/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Squad가 삭제되었습니다."));
    }

    @Test
    void 존재하지_않는_Squad_삭제_시_404_응답() throws Exception {
        willThrow(new NotFoundException(ErrorCode.SQUAD_NOT_FOUND)).given(squadService).delete(99L);

        mockMvc.perform(delete("/api/v1/squads/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SQUAD_NOT_FOUND"));
    }
}
