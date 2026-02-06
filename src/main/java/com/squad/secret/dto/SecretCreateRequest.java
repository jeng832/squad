package com.squad.secret.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Secret 생성 요청 DTO.
 *
 * <p>{@code value}는 저장 시 AES-256으로 암호화됩니다.</p>
 */
public record SecretCreateRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        String value
) {
}
