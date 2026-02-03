package com.squad.agent.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI 에이전트 엔티티.
 *
 * <p>{@code agents} 테이블에 매핑되며, 에이전트의 이름, 역할 유형, 역할 설명,
 * LLM 설정(JSON)을 관리합니다.</p>
 */
@Entity
@Table(name = "agents")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Convert(converter = RoleTypeConverter.class)
    @Column(name = "role_type", nullable = false)
    private RoleType roleType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String role;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "llm_config", nullable = false, columnDefinition = "JSON")
    private Map<String, Object> llmConfig;

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

    /**
     * 에이전트 정보를 수정합니다.
     */
    public void update(String name, String role, Map<String, Object> llmConfig) {
        this.name = name;
        this.role = role;
        this.llmConfig = llmConfig;
    }

    /**
     * RoleType과 데이터베이스 ENUM 값(소문자) 간의 변환을 처리하는 Converter.
     */
    @Converter
    static class RoleTypeConverter implements AttributeConverter<RoleType, String> {

        @Override
        public String convertToDatabaseColumn(RoleType attribute) {
            return attribute != null ? attribute.name().toLowerCase() : null;
        }

        @Override
        public RoleType convertToEntityAttribute(String dbData) {
            return dbData != null ? RoleType.valueOf(dbData.toUpperCase()) : null;
        }
    }
}
