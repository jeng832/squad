package com.squad.session.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Message (에이전트 간 메시지) 엔티티.
 *
 * <p>{@code messages} 테이블에 매핑되며, 세션 내 에이전트 간 통신 기록을 관리합니다.
 * {@code fromAgentId} / {@code toAgentId}는 시스템 메시지의 경우 null 가능합니다.</p>
 */
@Entity
@Table(name = "messages")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(name = "from_agent_id")
    private Long fromAgentId;

    @Column(name = "to_agent_id")
    private Long toAgentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private MessageType type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
