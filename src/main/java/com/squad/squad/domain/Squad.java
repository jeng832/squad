package com.squad.squad.domain;

import com.squad.agent.domain.Agent;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Squad (에이전트 팀 구성) 엔티티.
 *
 * <p>{@code squads} 테이블에 매핑되며, Orchestrator Agent와 멤버 Agent들의
 * 팀 구성을 관리합니다. Agent 멤버 목록은 {@code squad_agents} 조인 테이블로 관리됩니다.</p>
 */
@Entity
@Table(name = "squads")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Squad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orchestrator_id", nullable = false)
    private Agent orchestrator;

    @ManyToMany
    @JoinTable(
            name = "squad_agents",
            joinColumns = @JoinColumn(name = "squad_id"),
            inverseJoinColumns = @JoinColumn(name = "agent_id")
    )
    @Builder.Default
    private Set<Agent> agents = new HashSet<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "direct_communication", columnDefinition = "JSON")
    private Map<String, Object> directCommunication;

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

    public void update(String name, String description, Map<String, Object> directCommunication) {
        this.name = name;
        this.description = description;
        this.directCommunication = directCommunication;
    }

    public void addAgent(Agent agent) {
        this.agents.add(agent);
    }

    public void removeAgent(Long agentId) {
        this.agents.removeIf(a -> a.getId().equals(agentId));
    }
}
