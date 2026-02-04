package com.squad.session.repository;

import com.squad.agent.domain.Agent;
import com.squad.agent.domain.RoleType;
import com.squad.session.domain.Session;
import com.squad.session.domain.SessionStatus;
import com.squad.squad.domain.Squad;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SessionRepositoryTest {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Agent buildAgent(String name) {
        return Agent.builder()
                .name(name)
                .roleType(RoleType.ORCHESTRATOR)
                .role(name + " role")
                .llmConfig(Map.of("model", "claude-sonnet-4-20250514"))
                .build();
    }

    private Squad buildSquad(String name, Agent orchestrator) {
        return Squad.builder()
                .name(name)
                .orchestrator(orchestrator)
                .build();
    }

    private Session buildSession(Squad squad, String prompt) {
        return Session.builder()
                .squad(squad)
                .userPrompt(prompt)
                .build();
    }

    private Squad persistSquad() {
        Agent agent = entityManager.persist(buildAgent("orch"));
        entityManager.flush();
        Squad squad = entityManager.persist(buildSquad("alpha", agent));
        entityManager.flush();
        return squad;
    }

    @Test
    void Session_저장_후_ID로_조회_시_저장된_Session_반환() {
        Squad squad = persistSquad();

        Session session = buildSession(squad, "테스트 프롬프트");
        entityManager.persist(session);
        entityManager.flush();
        entityManager.clear();

        Session found = sessionRepository.findById(session.getId()).orElseThrow();

        assertThat(found.getUserPrompt()).isEqualTo("테스트 프롬프트");
        assertThat(found.getStatus()).isEqualTo(SessionStatus.PENDING);
        assertThat(found.getResult()).isNull();
        assertThat(found.getStartedAt()).isNull();
        assertThat(found.getCompletedAt()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void Session_Squad_조회_시_올바른_Squad_반환() {
        Squad squad = persistSquad();

        Session session = buildSession(squad, "테스트 프롬프트");
        entityManager.persist(session);
        entityManager.flush();
        entityManager.clear();

        Session found = sessionRepository.findById(session.getId()).orElseThrow();

        assertThat(found.getSquad().getId()).isEqualTo(squad.getId());
        assertThat(found.getSquad().getName()).isEqualTo("alpha");
    }

    @Test
    void Session_시작_후_저장_시_Running_상태_및_startedAt_반환() {
        Squad squad = persistSquad();

        Session session = buildSession(squad, "테스트 프롬프트");
        entityManager.persist(session);
        entityManager.flush();
        entityManager.clear();

        Session loaded = sessionRepository.findById(session.getId()).orElseThrow();
        loaded.start();
        sessionRepository.flush();
        entityManager.clear();

        Session found = sessionRepository.findById(session.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(SessionStatus.RUNNING);
        assertThat(found.getStartedAt()).isNotNull();
        assertThat(found.getCompletedAt()).isNull();
    }

    @Test
    void Session_완료_후_저장_시_Completed_상태_및_결과_반환() {
        Squad squad = persistSquad();

        Session session = buildSession(squad, "테스트 프롬프트");
        entityManager.persist(session);
        entityManager.flush();
        entityManager.clear();

        Session loaded = sessionRepository.findById(session.getId()).orElseThrow();
        loaded.start();
        loaded.complete("작업 완료 결과");
        sessionRepository.flush();
        entityManager.clear();

        Session found = sessionRepository.findById(session.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(found.getResult()).isEqualTo("작업 완료 결과");
        assertThat(found.getStartedAt()).isNotNull();
        assertThat(found.getCompletedAt()).isNotNull();
    }

    @Test
    void Session_취소_후_저장_시_Cancelled_상태_반환() {
        Squad squad = persistSquad();

        Session session = buildSession(squad, "테스트 프롬프트");
        entityManager.persist(session);
        entityManager.flush();
        entityManager.clear();

        Session loaded = sessionRepository.findById(session.getId()).orElseThrow();
        loaded.cancel();
        sessionRepository.flush();
        entityManager.clear();

        Session found = sessionRepository.findById(session.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(found.getCompletedAt()).isNotNull();
        assertThat(found.getResult()).isNull();
    }

    @Test
    void Session_SquadId로_조회_시_해당_Session_목록_반환() {
        Agent agent = entityManager.persist(buildAgent("orch"));
        entityManager.flush();
        Squad squad1 = entityManager.persist(buildSquad("squad1", agent));
        Squad squad2 = entityManager.persist(buildSquad("squad2", agent));
        entityManager.flush();

        entityManager.persist(buildSession(squad1, "프롬프트 A"));
        entityManager.persist(buildSession(squad1, "프롬프트 B"));
        entityManager.persist(buildSession(squad2, "프롬프트 C"));
        entityManager.flush();

        List<Session> sessions = sessionRepository.findBySquadId(squad1.getId());

        assertThat(sessions).hasSize(2);
        assertThat(sessions.stream().map(Session::getUserPrompt))
                .containsExactlyInAnyOrder("프롬프트 A", "프롬프트 B");
    }

    @Test
    void Session_Status로_조회_시_해당_Session_목록_반환() {
        Squad squad = persistSquad();

        Session pending = buildSession(squad, "pending");
        entityManager.persist(pending);
        entityManager.flush();

        Session running = buildSession(squad, "running");
        entityManager.persist(running);
        entityManager.flush();
        entityManager.clear();

        sessionRepository.findById(running.getId()).orElseThrow().start();
        sessionRepository.flush();

        List<Session> pendingSessions = sessionRepository.findByStatus(SessionStatus.PENDING);
        List<Session> runningSessions = sessionRepository.findByStatus(SessionStatus.RUNNING);

        assertThat(pendingSessions).hasSize(1);
        assertThat(pendingSessions.get(0).getUserPrompt()).isEqualTo("pending");
        assertThat(runningSessions).hasSize(1);
        assertThat(runningSessions.get(0).getUserPrompt()).isEqualTo("running");
    }
}
