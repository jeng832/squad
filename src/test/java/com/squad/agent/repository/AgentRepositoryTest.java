package com.squad.agent.repository;

import com.squad.agent.domain.Agent;
import com.squad.agent.domain.RoleType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AgentRepositoryTest {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Agent buildAgent(String name, RoleType roleType) {
        return Agent.builder()
                .name(name)
                .roleType(roleType)
                .role(name + " 역할 설명")
                .llmConfig(Map.of(
                        "provider", "claude",
                        "model", "claude-sonnet-4-20250514",
                        "apiKey", "ref:secret/claude-api-key"
                ))
                .build();
    }

    @Test
    void Agent_저장_후_ID로_조회_시_저장된_Agent_반환() {
        Agent agent = buildAgent("테스트 에이전트", RoleType.WORKER);

        Agent saved = entityManager.persistFlushPop(agent);

        Optional<Agent> found = agentRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("테스트 에이전트");
        assertThat(found.get().getRoleType()).isEqualTo(RoleType.WORKER);
        assertThat(found.get().getRole()).isEqualTo("테스트 에이전트 역할 설명");
        assertThat(found.get().getLlmConfig()).containsEntry("provider", "claude");
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void Agent_전체_조회_시_저장된_Agent_목록_반환() {
        agentRepository.save(buildAgent("에이전트 A", RoleType.ORCHESTRATOR));
        agentRepository.save(buildAgent("에이전트 B", RoleType.WORKER));
        agentRepository.save(buildAgent("에이전트 C", RoleType.ANALYST));

        List<Agent> agents = agentRepository.findAll();

        assertThat(agents).hasSize(3);
    }

    @Test
    void Agent_삭제_후_조회_시_빈_결과_반환() {
        Agent agent = entityManager.persistFlushPop(buildAgent("삭제 테스트", RoleType.SCRIBE));

        agentRepository.deleteById(agent.getId());
        agentRepository.flush();

        assertThat(agentRepository.findById(agent.getId())).isEmpty();
    }

    @Test
    void 각_RoleType별_저장_및_조회_시_올바른_타입_반환() {
        for (RoleType roleType : RoleType.values()) {
            agentRepository.save(buildAgent(roleType.name() + " Agent", roleType));
        }
        agentRepository.flush();

        List<Agent> agents = agentRepository.findAll();

        assertThat(agents).hasSize(RoleType.values().length);
        assertThat(agents.stream().map(Agent::getRoleType))
                .containsExactlyInAnyOrder(RoleType.values());
    }

    @Test
    void llmConfig_JSON_저장_후_조회_시_원본_데이터_반환() {
        Map<String, Object> llmConfig = Map.of(
                "provider", "claude",
                "model", "claude-sonnet-4-20250514",
                "apiKey", "ref:secret/claude-api-key"
        );
        Agent agent = Agent.builder()
                .name("JSON 테스트 에이전트")
                .roleType(RoleType.WORKER)
                .role("테스트")
                .llmConfig(llmConfig)
                .build();

        Agent saved = entityManager.persistFlushPop(agent);

        Agent found = agentRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getLlmConfig()).containsEntry("provider", "claude");
        assertThat(found.getLlmConfig()).containsEntry("model", "claude-sonnet-4-20250514");
        assertThat(found.getLlmConfig()).containsEntry("apiKey", "ref:secret/claude-api-key");
    }

    @Test
    void Agent_정보_수정_후_저장_시_수정된_값_반환() {
        Agent agent = entityManager.persistFlushPop(buildAgent("원본 이름", RoleType.WORKER));
        Map<String, Object> updatedConfig = Map.of("provider", "openai", "model", "gpt-4o");

        agent = agentRepository.findById(agent.getId()).orElseThrow();
        agent.update("수정된 이름", "수정된 역할", updatedConfig);
        agentRepository.flush();

        Agent found = agentRepository.findById(agent.getId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("수정된 이름");
        assertThat(found.getRole()).isEqualTo("수정된 역할");
        assertThat(found.getLlmConfig()).containsEntry("provider", "openai");
    }
}
