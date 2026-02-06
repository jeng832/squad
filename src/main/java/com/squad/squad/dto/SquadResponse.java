package com.squad.squad.dto;

import com.squad.squad.domain.Squad;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Squad 응답 DTO.
 */
public record SquadResponse(
        Long id,
        String name,
        String description,
        Long orchestratorId,
        Set<Long> agentIds,
        Map<String, Object> directCommunication,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SquadResponse from(Squad squad) {
        Set<Long> agentIdSet = squad.getAgents().stream()
                .map(agent -> agent.getId())
                .collect(Collectors.toSet());
        return new SquadResponse(
                squad.getId(),
                squad.getName(),
                squad.getDescription(),
                squad.getOrchestrator().getId(),
                agentIdSet,
                squad.getDirectCommunication(),
                squad.getCreatedAt(),
                squad.getUpdatedAt()
        );
    }
}
