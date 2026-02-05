package com.squad.skill.dto;

import com.squad.skill.domain.Skill;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Skill 응답 DTO.
 */
public record SkillResponse(
        Long id,
        String name,
        String description,
        String prompt,
        List<Long> requiredMcps,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SkillResponse from(Skill skill) {
        return new SkillResponse(
                skill.getId(),
                skill.getName(),
                skill.getDescription(),
                skill.getPrompt(),
                skill.getRequiredMcps(),
                skill.getCreatedAt(),
                skill.getUpdatedAt()
        );
    }
}
