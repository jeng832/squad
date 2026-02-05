package com.squad.secret.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Secret 수정 요청 DTO.
 *
 * <p>{@code value}는 저장 시 AES-256으로 암호화됩니다.</p>
 */
public record SecretUpdateRequest(

        @NotBlank
        String value
) {
}
