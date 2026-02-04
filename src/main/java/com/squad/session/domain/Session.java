package com.squad.session.domain;

import com.squad.squad.domain.Squad;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Session (작업 실행 단위) 엔티티.
 *
 * <p>{@code sessions} 테이블에 매핑되며, Squad를 기반으로 실행되는 작업 세션을 관리합니다.
 * 상태는 PENDING → RUNNING → COMPLETED / CANCELLED 로 전이됩니다.</p>
 */
@Entity
@Table(name = "sessions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "squad_id", nullable = false)
    private Squad squad;

    @Column(name = "user_prompt", nullable = false, columnDefinition = "TEXT")
    private String userPrompt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SessionStatus status = SessionStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void start() {
        this.status = SessionStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public void complete(String result) {
        this.status = SessionStatus.COMPLETED;
        this.result = result;
        this.completedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = SessionStatus.CANCELLED;
        this.completedAt = LocalDateTime.now();
    }
}
