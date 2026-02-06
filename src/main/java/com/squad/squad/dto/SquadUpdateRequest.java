package com.squad.squad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Squad 수정 요청 DTO.
 *
 * <p>Orchestrator는 생성 시 지정된 것을 유지하며 수정할 수 없습니다.</p>
 */
public record SquadUpdateRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        String description,

        List<Long> agentIds,

        Map<String, Object> directCommunication
) {
}
