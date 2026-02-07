package com.squad.session.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 세션 생성 요청 DTO.
 */
public record SessionCreateRequest(

        @NotNull
        Long squadId,

        @NotBlank
        String userPrompt
) {
}
