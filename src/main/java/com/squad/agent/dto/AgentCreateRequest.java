package com.squad.agent.dto;

import com.squad.agent.domain.RoleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 에이전트 생성 요청 DTO.
 */
public record AgentCreateRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @NotNull
        RoleType roleType,

        @NotBlank
        String role,

        @NotNull
        Map<String, Object> llmConfig
) {
}
