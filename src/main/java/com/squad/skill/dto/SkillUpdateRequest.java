package com.squad.skill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Skill 수정 요청 DTO.
 */
public record SkillUpdateRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        String description,

        @NotBlank
        String prompt,

        List<Long> requiredMcps
) {
}
