package com.squad.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 에이전트 수정 요청 DTO.
 */
public record AgentUpdateRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        String role,

        @NotNull
        Map<String, Object> llmConfig
) {
}
