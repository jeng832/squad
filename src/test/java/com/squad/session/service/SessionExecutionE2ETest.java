package com.squad.session.service;

import com.squad.agent.domain.Agent;
import com.squad.agent.domain.RoleType;
import com.squad.agent.repository.AgentRepository;
import com.squad.agent.runner.ContainerLifecycleManager;
import com.squad.common.exception.NotFoundException;
import com.squad.common.exception.ValidationException;
import com.squad.messaging.MessagePublisher;
import com.squad.messaging.redis.RedisTestContainerConfig;
import com.squad.monitoring.SessionEventPublisher;
import com.squad.orchestration.OrchestratorService;
import com.squad.session.domain.Session;
import com.squad.session.domain.SessionStatus;
import com.squad.session.dto.SessionCreateRequest;
import com.squad.session.dto.SessionResponse;
import com.squad.session.repository.SessionRepository;
import com.squad.squad.domain.Squad;
import com.squad.squad.repository.SquadRepository;
import com.squad.worker.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

/**
 * 세션 실행 E2E 테스트.
 *
 * <p>Spring 통합 테스트로 세션 생성 → 시작 → 완료 전체 흐름을 검증한다.
 * Docker Client와 LLM Provider는 Mock으로 대체하여 실제 외부 인프라 없이 테스트한다.</p>
 */
@SpringBootTest
@Transactional
@DisplayName("세션 실행 E2E 테스트")
class SessionExecutionE2ETest extends RedisTestContainerConfig {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private SessionExecutionService sessionExecutionService;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private SquadRepository squadRepository;

    @MockitoBean
    private ContainerLifecycleManager containerLifecycleManager;

    @MockitoBean
    private MessagePublisher messagePublisher;

    @MockitoBean
    private OrchestratorService orchestratorService;

    @MockitoBean
    private WorkerService workerService;

    @MockitoBean
    private SessionEventPublisher sessionEventPublisher;

    private Squad squad;
    private Agent orchestrator;
    private Agent worker;

    @BeforeEach
    void setUp() {
        orchestrator = agentRepository.save(Agent.builder()
                .name("test-orchestrator")
                .roleType(RoleType.ORCHESTRATOR)
                .role("테스트 오케스트레이터")
                .llmConfig(Map.of("provider", "claude", "model", "claude-sonnet-4-20250514"))
                .build());

        worker = agentRepository.save(Agent.builder()
                .name("test-worker")
                .roleType(RoleType.WORKER)
                .role("테스트 워커")
                .llmConfig(Map.of("provider", "claude", "model", "claude-sonnet-4-20250514"))
                .build());

        Squad newSquad = Squad.builder()
                .name("test-squad")
                .description("E2E 테스트용 Squad")
                .orchestrator(orchestrator)
                .build();
        newSquad.addAgent(worker);
        squad = squadRepository.save(newSquad);

        given(containerLifecycleManager.createAndStartContainer(anyString(), anyString(), anyList()))
                .willAnswer(inv -> "container-" + inv.getArgument(1));
        given(containerLifecycleManager.buildContainerName(anyString(), anyString()))
                .willAnswer(inv -> "squad-" + inv.getArgument(0) + "-" + inv.getArgument(1));
    }

    @Test
    @DisplayName("세션 생성 → 시작 → 완료 전체 흐름이 정상 동작한다")
    void fullLifecycle() {
        SessionResponse created = sessionService.create(
                new SessionCreateRequest(squad.getId(), "E2E 테스트 프롬프트"));
        assertThat(created.status()).isEqualTo(SessionStatus.PENDING);

        SessionResponse started = sessionExecutionService.start(created.id());
        assertThat(started.status()).isEqualTo(SessionStatus.RUNNING);

        verify(containerLifecycleManager, times(2))
                .createAndStartContainer(anyString(), anyString(), anyList());
        verify(messagePublisher).sendToAgent(argThat(msg ->
                msg.getContent().equals("E2E 테스트 프롬프트")));

        sessionExecutionService.complete(created.id(), "최종 결과입니다");

        Session completed = sessionRepository.findById(created.id()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(completed.getResult()).isEqualTo("최종 결과입니다");

        verify(workerService).stopAllWorkers(created.id());
        verify(sessionEventPublisher).publishSessionComplete(created.id(), "최종 결과입니다");
    }

    @Test
    @DisplayName("세션 시작 시 Container 생성 실패하면 정리 후 예외 전파")
    void startContainerFailureCleanup() {
        SessionResponse created = sessionService.create(
                new SessionCreateRequest(squad.getId(), "실패 테스트"));

        given(containerLifecycleManager.createAndStartContainer(
                anyString(), eq(String.valueOf(worker.getId())), anyList()))
                .willThrow(new RuntimeException("Docker 오류"));

        assertThatThrownBy(() -> sessionExecutionService.start(created.id()))
                .isInstanceOf(RuntimeException.class);

        verify(containerLifecycleManager, atLeastOnce())
                .stopAndRemoveContainer(anyString());
    }

    @Test
    @DisplayName("세션 취소 흐름이 정상 동작한다")
    void cancelFlow() {
        SessionResponse created = sessionService.create(
                new SessionCreateRequest(squad.getId(), "취소 테스트"));
        assertThat(created.status()).isEqualTo(SessionStatus.PENDING);

        SessionResponse cancelled = sessionService.cancel(created.id());
        assertThat(cancelled.status()).isEqualTo(SessionStatus.CANCELLED);

        Session session = sessionRepository.findById(created.id()).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(session.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 세션을 시작하면 NotFoundException 발생")
    void startNonExistentSession() {
        assertThatThrownBy(() -> sessionExecutionService.start(9999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("이미 시작된 세션을 다시 시작하면 ValidationException 발생")
    void startAlreadyRunningSession() {
        SessionResponse created = sessionService.create(
                new SessionCreateRequest(squad.getId(), "중복 시작 테스트"));

        sessionExecutionService.start(created.id());

        assertThatThrownBy(() -> sessionExecutionService.start(created.id()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Container 생성 실패 후 세션 상태는 PENDING으로 유지된다")
    void containerFailureKeepsPendingStatus() {
        SessionResponse created = sessionService.create(
                new SessionCreateRequest(squad.getId(), "상태 롤백 테스트"));

        given(containerLifecycleManager.createAndStartContainer(
                anyString(), eq(String.valueOf(worker.getId())), anyList()))
                .willThrow(new RuntimeException("Docker 오류"));

        assertThatThrownBy(() -> sessionExecutionService.start(created.id()))
                .isInstanceOf(RuntimeException.class);

        Session session = sessionRepository.findById(created.id()).orElseThrow();
        assertThat(session.getStatus()).isEqualTo(SessionStatus.PENDING);
    }

    @Test
    @DisplayName("COMPLETED 세션은 취소할 수 없다")
    void cannotCancelCompletedSession() {
        SessionResponse created = sessionService.create(
                new SessionCreateRequest(squad.getId(), "완료 후 취소 테스트"));

        sessionExecutionService.start(created.id());
        sessionExecutionService.complete(created.id(), "결과");

        assertThatThrownBy(() -> sessionService.cancel(created.id()))
                .isInstanceOf(ValidationException.class);
    }
}
