package com.squad.squad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Squad 생성 요청 DTO.
 *
 * <p>{@code orchestratorId}는 {@code roleType}이 {@code ORCHESTRATOR}인 Agent의 ID여야 합니다.</p>
 */
public record SquadCreateRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        String description,

        @NotNull
        Long orchestratorId,

        List<Long> agentIds,

        Map<String, Object> directCommunication
) {
}
