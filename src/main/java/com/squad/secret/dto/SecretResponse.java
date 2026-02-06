package com.squad.secret.dto;

import com.squad.secret.domain.Secret;

import java.time.LocalDateTime;

/**
 * Secret 응답 DTO.
 *
 * <p>암호화된 {@code value}는 응답에 포함되지 않습니다.
 * 실제 값은 참조 해결 엔드포인트({@code /api/v1/secrets/resolve})를 통해만 복호화 조회할 수 있습니다.</p>
 */
public record SecretResponse(
        Long id,
        String name,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SecretResponse from(Secret secret) {
        return new SecretResponse(
                secret.getId(),
                secret.getName(),
                secret.getCreatedAt(),
                secret.getUpdatedAt()
        );
    }
}
