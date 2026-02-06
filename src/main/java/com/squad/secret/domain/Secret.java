package com.squad.secret.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Secret (암호화된 민감 정보) 엔티티.
 *
 * <p>{@code secrets} 테이블에 매핑되며, {@code value} 컬럼에는 AES-256으로 암호화된 값이 저장됩니다.
 * 암호화/복호화는 {@link com.squad.secret.service.AesEncryptionUtil}을 통해 수행됩니다.</p>
 */
@Entity
@Table(name = "secrets")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Secret {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @Column(name = "encrypted_value", nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void update(String value) {
        this.value = value;
    }
}
