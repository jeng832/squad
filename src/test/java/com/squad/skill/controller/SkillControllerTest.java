package com.squad.skill.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import com.squad.skill.dto.SkillCreateRequest;
import com.squad.skill.dto.SkillResponse;
import com.squad.skill.dto.SkillUpdateRequest;
import com.squad.skill.service.SkillService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SkillController.class)
class SkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SkillService skillService;

    private static final LocalDateTime NOW = LocalDateTime.now();

    private SkillResponse sampleResponse() {
        return new SkillResponse(
                1L,
                "code-review",
                "코드 리뷰 스킬",
                "주어진 코드를 분석하고 리뷰 피드백을 작성하세요.",
                List.of(10L, 11L),
                NOW,
                NOW
        );
    }

    @Test
    void Skill_목록_조회_시_성공_응답_반환() throws Exception {
        given(skillService.findAll()).willReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("code-review"));
    }

    @Test
    void Skill_ID로_조회_시_성공_응답_반환() throws Exception {
        given(skillService.findById(1L)).willReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/skills/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.prompt").value("주어진 코드를 분석하고 리뷰 피드백을 작성하세요."));
    }

    @Test
    void 존재하지_않는_Skill_조회_시_404_응답() throws Exception {
        given(skillService.findById(99L)).willThrow(new NotFoundException(ErrorCode.SKILL_NOT_FOUND));

        mockMvc.perform(get("/api/v1/skills/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SKILL_NOT_FOUND"));
    }

    @Test
    void Skill_생성_시_201_응답_반환() throws Exception {
        SkillCreateRequest request = new SkillCreateRequest(
                "code-review",
                "코드 리뷰 스킬",
                "주어진 코드를 분석하고 리뷰 피드백을 작성하세요.",
                List.of(10L, 11L)
        );
        given(skillService.create(any(SkillCreateRequest.class))).willReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.message").value("Skill이 생성되었습니다."));
    }

    @Test
    void Skill_생성_시_requiredMcps_없이_성공() throws Exception {
        SkillCreateRequest request = new SkillCreateRequest(
                "simple-skill",
                null,
                "간단한 작업을 수행하세요.",
                null
        );
        SkillResponse response = new SkillResponse(2L, "simple-skill", null, "간단한 작업을 수행하세요.", null, NOW, NOW);
        given(skillService.create(any(SkillCreateRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requiredMcps").isEmpty());
    }

    @Test
    void Skill_생성_시_name_빈_값_400_응답() throws Exception {
        SkillCreateRequest request = new SkillCreateRequest(
                "",
                "설명",
                "프롬프트",
                null
        );

        mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void Skill_생성_시_prompt_빈_값_400_응답() throws Exception {
        // prompt를 빈 문자열로 설정
        Map<String, Object> body = Map.of(
                "name", "test-skill",
                "prompt", ""
        );

        mockMvc.perform(post("/api/v1/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void Skill_수정_시_성공_응답_반환() throws Exception {
        SkillUpdateRequest request = new SkillUpdateRequest(
                "code-review-v2",
                "업데이트된 코드 리뷰",
                "코드를 심도 있게 분석하여 리뷰하세요.",
                List.of(10L)
        );
        SkillResponse response = new SkillResponse(1L, request.name(), request.description(), request.prompt(), request.requiredMcps(), NOW, NOW);
        given(skillService.update(eq(1L), any(SkillUpdateRequest.class))).willReturn(response);

        mockMvc.perform(put("/api/v1/skills/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("code-review-v2"));
    }

    @Test
    void 존재하지_않는_Skill_수정_시_404_응답() throws Exception {
        SkillUpdateRequest request = new SkillUpdateRequest("name", "desc", "prompt", null);
        given(skillService.update(eq(99L), any(SkillUpdateRequest.class)))
                .willThrow(new NotFoundException(ErrorCode.SKILL_NOT_FOUND));

        mockMvc.perform(put("/api/v1/skills/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SKILL_NOT_FOUND"));
    }

    @Test
    void Skill_삭제_시_성공_응답_반환() throws Exception {
        willDoNothing().given(skillService).delete(1L);

        mockMvc.perform(delete("/api/v1/skills/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Skill이 삭제되었습니다."));
    }

    @Test
    void 존재하지_않는_Skill_삭제_시_404_응답() throws Exception {
        willThrow(new NotFoundException(ErrorCode.SKILL_NOT_FOUND)).given(skillService).delete(99L);

        mockMvc.perform(delete("/api/v1/skills/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("SKILL_NOT_FOUND"));
    }
}
