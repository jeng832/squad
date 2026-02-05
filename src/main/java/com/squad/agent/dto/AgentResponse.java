package com.squad.agent.dto;

import com.squad.agent.domain.Agent;
import com.squad.agent.domain.RoleType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 에이전트 응답 DTO.
 */
public record AgentResponse(
        Long id,
        String name,
        RoleType roleType,
        String role,
        Map<String, Object> llmConfig,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AgentResponse from(Agent agent) {
        return new AgentResponse(
                agent.getId(),
                agent.getName(),
                agent.getRoleType(),
                agent.getRole(),
                agent.getLlmConfig(),
                agent.getCreatedAt(),
                agent.getUpdatedAt()
        );
    }
}
