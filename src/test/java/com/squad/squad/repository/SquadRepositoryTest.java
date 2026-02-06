package com.squad.squad.repository;

import com.squad.agent.domain.Agent;
import com.squad.agent.domain.RoleType;
import com.squad.squad.domain.Squad;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SquadRepositoryTest {

    @Autowired
    private SquadRepository squadRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Agent buildAgent(String name, RoleType roleType) {
        return Agent.builder()
                .name(name)
                .roleType(roleType)
                .role(name + " role")
                .llmConfig(Map.of("model", "claude-sonnet-4-20250514"))
                .build();
    }

    private Squad buildSquad(String name, Agent orchestrator) {
        return Squad.builder()
                .name(name)
                .description(name + " 설명")
                .orchestrator(orchestrator)
                .build();
    }

    @Test
    void Squad_저장_후_ID로_조회_시_저장된_Squad_반환() {
        Agent orchestrator = entityManager.persist(buildAgent("orch", RoleType.ORCHESTRATOR));
        entityManager.flush();

        Squad squad = buildSquad("alpha", orchestrator);
        entityManager.persist(squad);
        entityManager.flush();
        entityManager.clear();

        Squad found = squadRepository.findById(squad.getId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("alpha");
        assertThat(found.getDescription()).isEqualTo("alpha 설명");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void Squad_Orchestrator_조회_시_올바른_Agent_반환() {
        Agent orchestrator = entityManager.persist(buildAgent("orch", RoleType.ORCHESTRATOR));
        entityManager.flush();

        Squad squad = buildSquad("alpha", orchestrator);
        entityManager.persist(squad);
        entityManager.flush();
        entityManager.clear();

        Squad found = squadRepository.findById(squad.getId()).orElseThrow();

        assertThat(found.getOrchestrator().getId()).isEqualTo(orchestrator.getId());
        assertThat(found.getOrchestrator().getName()).isEqualTo("orch");
        assertThat(found.getOrchestrator().getRoleType()).isEqualTo(RoleType.ORCHESTRATOR);
    }

    @Test
    void Squad_에_Agent_추가_후_조회_시_Agent_목록_반환() {
        Agent orchestrator = entityManager.persist(buildAgent("orch", RoleType.ORCHESTRATOR));
        Agent worker1 = entityManager.persist(buildAgent("worker1", RoleType.WORKER));
        Agent worker2 = entityManager.persist(buildAgent("worker2", RoleType.WORKER));
        entityManager.flush();

        Squad squad = Squad.builder()
                .name("alpha")
                .orchestrator(orchestrator)
                .agents(Set.of(orchestrator, worker1, worker2))
                .build();
        entityManager.persist(squad);
        entityManager.flush();
        entityManager.clear();

        Squad found = squadRepository.findById(squad.getId()).orElseThrow();

        assertThat(found.getAgents()).hasSize(3);
        assertThat(found.getAgents().stream().map(Agent::getName))
                .containsExactlyInAnyOrder("orch", "worker1", "worker2");
    }

    @Test
    void Squad_에서_Agent_제거_후_조회_시_최신_목록_반환() {
        Agent orchestrator = entityManager.persist(buildAgent("orch", RoleType.ORCHESTRATOR));
        Agent worker1 = entityManager.persist(buildAgent("worker1", RoleType.WORKER));
        Agent worker2 = entityManager.persist(buildAgent("worker2", RoleType.WORKER));
        entityManager.flush();

        Squad squad = Squad.builder()
                .name("alpha")
                .orchestrator(orchestrator)
                .agents(Set.of(orchestrator, worker1, worker2))
                .build();
        entityManager.persist(squad);
        entityManager.flush();
        entityManager.clear();

        Squad loaded = squadRepository.findById(squad.getId()).orElseThrow();
        loaded.removeAgent(worker2.getId());
        squadRepository.flush();
        entityManager.clear();

        Squad found = squadRepository.findById(squad.getId()).orElseThrow();

        assertThat(found.getAgents()).hasSize(2);
        assertThat(found.getAgents().stream().map(Agent::getName))
                .containsExactlyInAnyOrder("orch", "worker1");
    }

    @Test
    void Squad_OrchestraterId로_조회_시_해당_Squad_목록_반환() {
        Agent orch1 = entityManager.persist(buildAgent("orch1", RoleType.ORCHESTRATOR));
        Agent orch2 = entityManager.persist(buildAgent("orch2", RoleType.ORCHESTRATOR));
        entityManager.flush();

        entityManager.persist(buildSquad("squad1", orch1));
        entityManager.persist(buildSquad("squad2", orch1));
        entityManager.persist(buildSquad("squad3", orch2));
        entityManager.flush();

        List<Squad> squads = squadRepository.findByOrchestratorId(orch1.getId());

        assertThat(squads).hasSize(2);
        assertThat(squads.stream().map(Squad::getName))
                .containsExactlyInAnyOrder("squad1", "squad2");
    }

    @Test
    void Squad_전체_조회_시_저장된_Squad_목록_반환() {
        Agent orchestrator = entityManager.persist(buildAgent("orch", RoleType.ORCHESTRATOR));
        entityManager.flush();

        squadRepository.save(buildSquad("alpha", orchestrator));
        squadRepository.save(buildSquad("beta", orchestrator));
        squadRepository.save(buildSquad("gamma", orchestrator));

        List<Squad> squads = squadRepository.findAll();

        assertThat(squads).hasSize(3);
    }

    @Test
    void Squad_정보_수정_후_저장_시_수정된_값_반환() {
        Agent orchestrator = entityManager.persist(buildAgent("orch", RoleType.ORCHESTRATOR));
        entityManager.flush();

        Squad squad = buildSquad("alpha", orchestrator);
        entityManager.persist(squad);
        entityManager.flush();
        entityManager.clear();

        Squad loaded = squadRepository.findById(squad.getId()).orElseThrow();
        loaded.update("alpha-v2", "수정된 설명", Map.of("enabled", true));
        squadRepository.flush();

        Squad found = squadRepository.findById(squad.getId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("alpha-v2");
        assertThat(found.getDescription()).isEqualTo("수정된 설명");
        assertThat(found.getDirectCommunication()).containsEntry("enabled", true);
    }

    @Test
    void direct_communication_JSON_저장_후_조회_시_원본_데이터_반환() {
        Agent orchestrator = entityManager.persist(buildAgent("orch", RoleType.ORCHESTRATOR));
        entityManager.flush();

        Map<String, Object> directComm = Map.of(
                "enabled", true,
                "rules", List.of("worker1 -> worker2", "worker2 -> worker1")
        );
        Squad squad = Squad.builder()
                .name("alpha")
                .orchestrator(orchestrator)
                .directCommunication(directComm)
                .build();
        entityManager.persist(squad);
        entityManager.flush();
        entityManager.clear();

        Squad found = squadRepository.findById(squad.getId()).orElseThrow();

        assertThat(found.getDirectCommunication()).containsEntry("enabled", true);
        assertThat(found.getDirectCommunication().get("rules")).isInstanceOf(List.class);
        assertThat((List<?>) found.getDirectCommunication().get("rules")).hasSize(2);
    }
}
