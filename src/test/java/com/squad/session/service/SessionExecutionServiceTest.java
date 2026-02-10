package com.squad.session.service;

import com.squad.agent.domain.Agent;
import com.squad.agent.domain.RoleType;
import com.squad.agent.runner.ContainerLifecycleManager;
import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import com.squad.common.exception.ValidationException;
import com.squad.messaging.MessagePublisher;
import com.squad.messaging.SessionMessage;
import com.squad.orchestration.OrchestratorService;
import com.squad.session.domain.MessageType;
import com.squad.session.domain.Session;
import com.squad.session.domain.SessionStatus;
import com.squad.session.dto.SessionResponse;
import com.squad.session.repository.SessionRepository;
import com.squad.squad.domain.Squad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionExecutionService 단위 테스트")
class SessionExecutionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private ContainerLifecycleManager containerLifecycleManager;

    @Mock
    private MessagePublisher messagePublisher;

    @Mock
    private OrchestratorService orchestratorService;

    @InjectMocks
    private SessionExecutionService sessionExecutionService;

    private Agent createAgent(Long id, String name, RoleType roleType) {
        return Agent.builder()
                .id(id)
                .name(name)
                .roleType(roleType)
                .role("테스트 역할")
                .build();
    }

    private Squad createSquad(Agent orchestrator, Set<Agent> agents) {
        return Squad.builder()
                .id(1L)
                .name("테스트 스쿼드")
                .orchestrator(orchestrator)
                .agents(agents)
                .build();
    }

    private Session createPendingSession(Squad squad) {
        return Session.builder()
                .id(1L)
                .squad(squad)
                .userPrompt("테스트 프롬프트")
                .status(SessionStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("PENDING 상태의 세션을 성공적으로 시작한다")
    void startSuccess() {
        Agent orchestrator = createAgent(10L, "orchestrator", RoleType.ORCHESTRATOR);
        Agent worker = createAgent(20L, "worker", RoleType.WORKER);
        Squad squad = createSquad(orchestrator, Set.of(worker));
        Session session = createPendingSession(squad);

        given(sessionRepository.findById(1L)).willReturn(Optional.of(session));
        given(containerLifecycleManager.createAndStartContainer(eq("1"), eq("10"), anyList()))
                .willReturn("container-orchestrator");
        given(containerLifecycleManager.createAndStartContainer(eq("1"), eq("20"), anyList()))
                .willReturn("container-worker");
        willDoNothing().given(messagePublisher).sendToAgent(any(SessionMessage.class));

        SessionResponse response = sessionExecutionService.start(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo(SessionStatus.RUNNING);
        verify(containerLifecycleManager, times(2)).createAndStartContainer(anyString(), anyString(), anyList());
        verify(messagePublisher).sendToAgent(argThat(msg ->
                msg.getType() == MessageType.TASK_REQUEST
                        && msg.getToAgentId().equals(10L)
                        && msg.getContent().equals("테스트 프롬프트")
        ));
        verify(sessionRepository).flush();
    }

    @Test
    @DisplayName("존재하지 않는 세션 ID로 시작하면 NotFoundException 발생")
    void startWithNonExistentSession() {
        given(sessionRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sessionExecutionService.start(99L))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(containerLifecycleManager);
        verifyNoInteractions(messagePublisher);
    }

    @Test
    @DisplayName("PENDING이 아닌 상태의 세션을 시작하면 ValidationException 발생")
    void startWithNonPendingSession() {
        Agent orchestrator = createAgent(10L, "orchestrator", RoleType.ORCHESTRATOR);
        Squad squad = createSquad(orchestrator, Set.of());
        Session session = Session.builder()
                .id(1L)
                .squad(squad)
                .userPrompt("프롬프트")
                .status(SessionStatus.RUNNING)
                .build();

        given(sessionRepository.findById(1L)).willReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionExecutionService.start(1L))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(containerLifecycleManager);
        verifyNoInteractions(messagePublisher);
    }

    @Test
    @DisplayName("Container 시작 실패 시 이미 시작된 Container를 정리한다")
    void startContainerFailureCleansUp() {
        Agent orchestrator = createAgent(10L, "orchestrator", RoleType.ORCHESTRATOR);
        Agent worker = createAgent(20L, "worker", RoleType.WORKER);
        Squad squad = createSquad(orchestrator, Set.of(worker));
        Session session = createPendingSession(squad);

        given(sessionRepository.findById(1L)).willReturn(Optional.of(session));
        given(containerLifecycleManager.createAndStartContainer(eq("1"), eq("10"), anyList()))
                .willReturn("container-orchestrator");
        given(containerLifecycleManager.createAndStartContainer(eq("1"), eq("20"), anyList()))
                .willThrow(new RuntimeException("Docker 오류"));

        assertThatThrownBy(() -> sessionExecutionService.start(1L))
                .isInstanceOf(RuntimeException.class);

        verify(containerLifecycleManager).stopAndRemoveContainer("container-orchestrator");
        verifyNoInteractions(messagePublisher);
    }

    @Test
    @DisplayName("DB flush 실패 시 Container를 정리하고 예외를 전파한다")
    void startDbFlushFailureCleansUp() {
        Agent orchestrator = createAgent(10L, "orchestrator", RoleType.ORCHESTRATOR);
        Squad squad = createSquad(orchestrator, Set.of());
        Session session = createPendingSession(squad);

        given(sessionRepository.findById(1L)).willReturn(Optional.of(session));
        given(containerLifecycleManager.createAndStartContainer(eq("1"), eq("10"), anyList()))
                .willReturn("container-orchestrator");
        doThrow(new RuntimeException("DB 오류")).when(sessionRepository).flush();

        assertThatThrownBy(() -> sessionExecutionService.start(1L))
                .isInstanceOf(RuntimeException.class);

        verify(containerLifecycleManager).stopAndRemoveContainer("container-orchestrator");
        verifyNoInteractions(messagePublisher);
    }

    @Test
    @DisplayName("프롬프트 전송 실패 시 Container를 정리하고 예외를 전파한다")
    void startPromptSendFailureCleansUp() {
        Agent orchestrator = createAgent(10L, "orchestrator", RoleType.ORCHESTRATOR);
        Squad squad = createSquad(orchestrator, Set.of());
        Session session = createPendingSession(squad);

        given(sessionRepository.findById(1L)).willReturn(Optional.of(session));
        given(containerLifecycleManager.createAndStartContainer(eq("1"), eq("10"), anyList()))
                .willReturn("container-orchestrator");
        doThrow(new RuntimeException("Redis 오류")).when(messagePublisher).sendToAgent(any(SessionMessage.class));

        assertThatThrownBy(() -> sessionExecutionService.start(1L))
                .isInstanceOf(RuntimeException.class);

        verify(containerLifecycleManager).stopAndRemoveContainer("container-orchestrator");
    }

    @Test
    @DisplayName("Orchestrator가 agents에 포함되어도 중복 시작하지 않는다")
    void startSkipsOrchestratorInAgents() {
        Agent orchestrator = createAgent(10L, "orchestrator", RoleType.ORCHESTRATOR);
        Agent worker = createAgent(20L, "worker", RoleType.WORKER);
        Squad squad = createSquad(orchestrator, Set.of(orchestrator, worker));
        Session session = createPendingSession(squad);

        given(sessionRepository.findById(1L)).willReturn(Optional.of(session));
        given(containerLifecycleManager.createAndStartContainer(eq("1"), eq("10"), anyList()))
                .willReturn("container-orchestrator");
        given(containerLifecycleManager.createAndStartContainer(eq("1"), eq("20"), anyList()))
                .willReturn("container-worker");

        SessionResponse response = sessionExecutionService.start(1L);

        assertThat(response.status()).isEqualTo(SessionStatus.RUNNING);
        verify(containerLifecycleManager, times(2)).createAndStartContainer(anyString(), anyString(), anyList());
        verify(containerLifecycleManager).createAndStartContainer(eq("1"), eq("10"), anyList());
        verify(containerLifecycleManager).createAndStartContainer(eq("1"), eq("20"), anyList());
    }

    @Test
    @DisplayName("Agent가 없는 Squad도 Orchestrator만으로 시작할 수 있다")
    void startWithOrchestratorOnly() {
        Agent orchestrator = createAgent(10L, "orchestrator", RoleType.ORCHESTRATOR);
        Squad squad = createSquad(orchestrator, Set.of());
        Session session = createPendingSession(squad);

        given(sessionRepository.findById(1L)).willReturn(Optional.of(session));
        given(containerLifecycleManager.createAndStartContainer(eq("1"), eq("10"), anyList()))
                .willReturn("container-orchestrator");

        SessionResponse response = sessionExecutionService.start(1L);

        assertThat(response.status()).isEqualTo(SessionStatus.RUNNING);
        verify(containerLifecycleManager, times(1)).createAndStartContainer(anyString(), anyString(), anyList());
        verify(messagePublisher).sendToAgent(any(SessionMessage.class));
    }
}
