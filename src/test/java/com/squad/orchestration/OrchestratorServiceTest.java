package com.squad.orchestration;

import com.squad.agent.domain.Agent;
import com.squad.agent.domain.RoleType;
import com.squad.llm.LlmProvider;
import com.squad.llm.LlmProviderFactory;
import com.squad.llm.model.*;
import com.squad.messaging.*;
import com.squad.monitoring.SessionEventPublisher;
import com.squad.secret.service.SecretService;
import com.squad.session.domain.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrchestratorService 단위 테스트")
class OrchestratorServiceTest {

    @Mock
    private LlmProviderFactory llmProviderFactory;

    @Mock
    private MessageRouter messageRouter;

    @Mock
    private MessageSubscriber messageSubscriber;

    @Mock
    private LlmProvider llmProvider;

    @Mock
    private Subscription agentSubscription;

    @Mock
    private Subscription orchestratorSubscription;

    @Mock
    private SessionEventPublisher sessionEventPublisher;

    @Mock
    private SessionCompleteHandler completeHandler;

    @Mock
    private SecretService secretService;

    private OrchestratorService orchestratorService;

    private Agent orchestrator;
    private Agent worker;
    private Set<Agent> agents;

    @BeforeEach
    void setUp() {
        orchestratorService = new OrchestratorService(llmProviderFactory, messageRouter, messageSubscriber, sessionEventPublisher, secretService);

        orchestrator = Agent.builder()
                .id(1L)
                .name("Orchestrator")
                .roleType(RoleType.ORCHESTRATOR)
                .role("작업을 분배하고 조율합니다")
                .llmConfig(Map.of("provider", "claude", "model", "claude-sonnet-4-20250514",
                        "apiKey", "sk-test-key"))
                .build();

        worker = Agent.builder()
                .id(2L)
                .name("Worker")
                .roleType(RoleType.WORKER)
                .role("코드를 작성합니다")
                .llmConfig(Map.of("provider", "claude", "apiKey", "sk-test-key"))
                .build();

        agents = Set.of(worker);
    }

    @Test
    @DisplayName("startOrchestration 호출 시 Agent 채널과 Orchestrator 채널을 구독한다")
    void startOrchestrationSubscribes() {
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(1L), any(MessageHandler.class)))
                .willReturn(agentSubscription);
        given(messageSubscriber.subscribeToOrchestrator(eq(1L), any(MessageHandler.class)))
                .willReturn(orchestratorSubscription);

        orchestratorService.startOrchestration(1L, orchestrator, agents, completeHandler);

        verify(messageSubscriber).subscribeToAgent(eq(1L), eq(1L), any(MessageHandler.class));
        verify(messageSubscriber).subscribeToOrchestrator(eq(1L), any(MessageHandler.class));
    }

    @Test
    @DisplayName("stopOrchestration 호출 시 모든 구독을 해제한다")
    void stopOrchestrationUnsubscribes() {
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(1L), any(MessageHandler.class)))
                .willReturn(agentSubscription);
        given(messageSubscriber.subscribeToOrchestrator(eq(1L), any(MessageHandler.class)))
                .willReturn(orchestratorSubscription);

        orchestratorService.startOrchestration(1L, orchestrator, agents, completeHandler);
        orchestratorService.stopOrchestration(1L);

        verify(agentSubscription).unsubscribe();
        verify(orchestratorSubscription).unsubscribe();
    }

    @Test
    @DisplayName("TASK_REQUEST 수신 시 LLM을 호출하고 delegate_task로 Agent에게 작업을 분배한다")
    void taskRequestDelegatesToAgent() {
        // Given: subscription 설정
        ArgumentCaptor<MessageHandler> agentHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(1L), agentHandlerCaptor.capture()))
                .willReturn(agentSubscription);
        given(messageSubscriber.subscribeToOrchestrator(eq(1L), any(MessageHandler.class)))
                .willReturn(orchestratorSubscription);

        // Given: LLM 응답 - delegate_task tool call
        LlmToolCall delegateCall = new LlmToolCall("call-1", "delegate_task",
                Map.of("agent_id", 2, "task", "코드를 작성해주세요"));
        LlmResponse llmResponse = new LlmResponse("resp-1", null, "tool_use",
                List.of(delegateCall), null);

        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.of(llmProvider));
        given(llmProvider.sendMessage(any(LlmRequest.class))).willReturn(llmResponse);

        // When
        orchestratorService.startOrchestration(1L, orchestrator, agents, completeHandler);
        SessionMessage prompt = SessionMessage.of(1L, null, 1L, MessageType.TASK_REQUEST, "프로젝트를 분석해주세요");
        agentHandlerCaptor.getValue().handle(prompt);

        // Then: Agent에게 TASK_REQUEST가 라우팅됨
        ArgumentCaptor<SessionMessage> routedMessage = ArgumentCaptor.forClass(SessionMessage.class);
        verify(messageRouter).route(routedMessage.capture());

        SessionMessage delegated = routedMessage.getValue();
        assertThat(delegated.getType()).isEqualTo(MessageType.TASK_REQUEST);
        assertThat(delegated.getFromAgentId()).isEqualTo(1L);
        assertThat(delegated.getToAgentId()).isEqualTo(2L);
        assertThat(delegated.getContent()).isEqualTo("코드를 작성해주세요");
    }

    @Test
    @DisplayName("종합 작업 위임 시 이전 Agent 결과가 task 본문에 포함된다")
    void synthesisTaskIncludesPreviousAgentResults() {
        Agent docWorker = Agent.builder()
                .id(3L)
                .name("DocWorker")
                .roleType(RoleType.WORKER)
                .role("결과를 종합합니다")
                .llmConfig(Map.of("provider", "claude"))
                .build();

        ArgumentCaptor<MessageHandler> agentHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        ArgumentCaptor<MessageHandler> orchHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(1L), agentHandlerCaptor.capture()))
                .willReturn(agentSubscription);
        given(messageSubscriber.subscribeToOrchestrator(eq(1L), orchHandlerCaptor.capture()))
                .willReturn(orchestratorSubscription);

        LlmResponse firstResponse = new LlmResponse("resp-1", null, "tool_use",
                List.of(new LlmToolCall("call-1", "delegate_task", Map.of("agent_id", 2, "task", "코드 분석"))), null);
        LlmResponse secondResponse = new LlmResponse("resp-2", null, "tool_use",
                List.of(new LlmToolCall("call-2", "delegate_task", Map.of("agent_id", 3, "task", "분석 결과를 바탕으로 종합해줘"))), null);
        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.of(llmProvider));
        given(llmProvider.sendMessage(any(LlmRequest.class)))
                .willReturn(firstResponse)
                .willReturn(secondResponse);

        orchestratorService.startOrchestration(1L, orchestrator, Set.of(worker, docWorker), completeHandler);
        agentHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, null, 1L, MessageType.TASK_REQUEST, "시작"));
        orchHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, 2L, null, MessageType.TASK_RESULT, "핵심 포인트 A\n핵심 포인트 B"));

        ArgumentCaptor<SessionMessage> routedMessage = ArgumentCaptor.forClass(SessionMessage.class);
        verify(messageRouter, times(2)).route(routedMessage.capture());
        List<SessionMessage> routed = routedMessage.getAllValues();

        SessionMessage secondDelegation = routed.get(1);
        assertThat(secondDelegation.getToAgentId()).isEqualTo(3L);
        assertThat(secondDelegation.getContent()).contains("[참고: 이전 Agent 결과]");
        assertThat(secondDelegation.getContent()).contains("핵심 포인트 A");
    }

    @Test
    @DisplayName("이전 결과 없는 종합 요청은 가드 문구를 추가해 위임한다")
    void synthesisTaskWithoutResultsAddsGuardPrefix() {
        ArgumentCaptor<MessageHandler> agentHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(1L), agentHandlerCaptor.capture()))
                .willReturn(agentSubscription);
        given(messageSubscriber.subscribeToOrchestrator(eq(1L), any(MessageHandler.class)))
                .willReturn(orchestratorSubscription);

        LlmToolCall delegateCall = new LlmToolCall("call-1", "delegate_task",
                Map.of("agent_id", 2, "task", "기존 결과를 바탕으로 종합 정리해줘"));
        LlmResponse llmResponse = new LlmResponse("resp-1", null, "tool_use", List.of(delegateCall), null);
        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.of(llmProvider));
        given(llmProvider.sendMessage(any(LlmRequest.class))).willReturn(llmResponse);

        orchestratorService.startOrchestration(1L, orchestrator, agents, completeHandler);
        agentHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, null, 1L, MessageType.TASK_REQUEST, "시작"));

        ArgumentCaptor<SessionMessage> routedMessage = ArgumentCaptor.forClass(SessionMessage.class);
        verify(messageRouter).route(routedMessage.capture());
        assertThat(routedMessage.getValue().getContent()).startsWith("[가드] 이전 Agent 분석 결과가 아직 없어");
    }

    @Test
    @DisplayName("같은 Agent에 대한 유사한 task 재위임은 dedupe로 차단된다")
    void similarTaskDelegationIsDeduped() {
        ArgumentCaptor<MessageHandler> agentHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        ArgumentCaptor<MessageHandler> orchHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(1L), agentHandlerCaptor.capture()))
                .willReturn(agentSubscription);
        given(messageSubscriber.subscribeToOrchestrator(eq(1L), orchHandlerCaptor.capture()))
                .willReturn(orchestratorSubscription);

        LlmResponse firstResponse = new LlmResponse("resp-1", null, "tool_use",
                List.of(new LlmToolCall("call-1", "delegate_task",
                        Map.of("agent_id", 2, "task", "SessionExecutionService 실패 지점을 분석해줘"))), null);
        LlmResponse secondResponse = new LlmResponse("resp-2", null, "tool_use",
                List.of(new LlmToolCall("call-2", "delegate_task",
                        Map.of("agent_id", 2, "task", "SessionExecutionService 실패 지점 분석 부탁해"))), null);
        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.of(llmProvider));
        given(llmProvider.sendMessage(any(LlmRequest.class)))
                .willReturn(firstResponse)
                .willReturn(secondResponse);

        orchestratorService.startOrchestration(1L, orchestrator, agents, completeHandler);
        agentHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, null, 1L, MessageType.TASK_REQUEST, "시작"));
        orchHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, 2L, null, MessageType.TASK_RESULT, "1차 분석 결과"));

        verify(messageRouter, times(1)).route(any(SessionMessage.class));
    }

    @Test
    @DisplayName("TASK_RESULT 수신 후 모든 작업 완료 시 LLM을 재호출한다")
    void taskResultTriggersLlmCall() {
        // Given: subscription 설정
        ArgumentCaptor<MessageHandler> agentHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        ArgumentCaptor<MessageHandler> orchHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(1L), agentHandlerCaptor.capture()))
                .willReturn(agentSubscription);
        given(messageSubscriber.subscribeToOrchestrator(eq(1L), orchHandlerCaptor.capture()))
                .willReturn(orchestratorSubscription);

        // Given: 1차 LLM 응답 - delegate_task
        LlmToolCall delegateCall = new LlmToolCall("call-1", "delegate_task",
                Map.of("agent_id", 2, "task", "코드 작성"));
        LlmResponse firstResponse = new LlmResponse("resp-1", null, "tool_use",
                List.of(delegateCall), null);

        // Given: 2차 LLM 응답 - complete_session
        LlmToolCall completeCall = new LlmToolCall("call-2", "complete_session",
                Map.of("result", "모든 작업이 완료되었습니다"));
        LlmResponse secondResponse = new LlmResponse("resp-2", null, "tool_use",
                List.of(completeCall), null);

        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.of(llmProvider));
        given(llmProvider.sendMessage(any(LlmRequest.class)))
                .willReturn(firstResponse)
                .willReturn(secondResponse);

        // When: 프롬프트 수신 → 작업 분배
        orchestratorService.startOrchestration(1L, orchestrator, agents, completeHandler);
        agentHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, null, 1L, MessageType.TASK_REQUEST, "분석해주세요"));

        // When: Agent 결과 수신
        orchHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, 2L, null, MessageType.TASK_RESULT, "분석 결과입니다"));

        // Then: LLM이 2번 호출됨 (프롬프트 + 결과 수신 후)
        verify(llmProvider, times(2)).sendMessage(any(LlmRequest.class));

        // Then: TASK_REQUEST + SYSTEM 메시지가 라우팅됨
        verify(messageRouter, times(2)).route(any(SessionMessage.class));
    }

    @Test
    @DisplayName("complete_session tool 호출 시 SYSTEM 메시지를 브로드캐스트한다")
    void completeSessionBroadcasts() {
        ArgumentCaptor<MessageHandler> agentHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(1L), agentHandlerCaptor.capture()))
                .willReturn(agentSubscription);
        given(messageSubscriber.subscribeToOrchestrator(eq(1L), any(MessageHandler.class)))
                .willReturn(orchestratorSubscription);

        LlmToolCall completeCall = new LlmToolCall("call-1", "complete_session",
                Map.of("result", "최종 결과입니다"));
        LlmResponse llmResponse = new LlmResponse("resp-1", null, "tool_use",
                List.of(completeCall), null);

        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.of(llmProvider));
        given(llmProvider.sendMessage(any(LlmRequest.class))).willReturn(llmResponse);

        orchestratorService.startOrchestration(1L, orchestrator, agents, completeHandler);
        agentHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, null, 1L, MessageType.TASK_REQUEST, "간단한 질문"));

        ArgumentCaptor<SessionMessage> routedMessage = ArgumentCaptor.forClass(SessionMessage.class);
        verify(messageRouter).route(routedMessage.capture());

        SessionMessage systemMsg = routedMessage.getValue();
        assertThat(systemMsg.getType()).isEqualTo(MessageType.SYSTEM);
        assertThat(systemMsg.getContent()).isEqualTo("최종 결과입니다");
    }

    @Test
    @DisplayName("시스템 프롬프트에 Agent 정보가 포함된다")
    void systemPromptContainsAgentInfo() {
        String prompt = orchestratorService.buildSystemPrompt(orchestrator, agents);

        assertThat(prompt).contains("Orchestrator");
        assertThat(prompt).contains("작업을 분배하고 조율합니다");
        assertThat(prompt).contains("Worker");
        assertThat(prompt).contains("WORKER");
        assertThat(prompt).contains("delegate_task");
        assertThat(prompt).contains("complete_session");
    }

    @Test
    @DisplayName("delegate_task와 complete_session 도구가 정의된다")
    void toolsIncludeDelegateAndComplete() {
        List<LlmTool> tools = orchestratorService.buildTools(agents);

        assertThat(tools).hasSize(2);
        assertThat(tools).extracting(LlmTool::name)
                .containsExactlyInAnyOrder("delegate_task", "complete_session");
    }

    @Test
    @DisplayName("LLM Provider를 찾을 수 없으면 예외가 발생한다")
    void unknownProviderThrows() {
        ArgumentCaptor<MessageHandler> agentHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(1L), agentHandlerCaptor.capture()))
                .willReturn(agentSubscription);
        given(messageSubscriber.subscribeToOrchestrator(eq(1L), any(MessageHandler.class)))
                .willReturn(orchestratorSubscription);
        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.empty());

        orchestratorService.startOrchestration(1L, orchestrator, agents, completeHandler);

        // handleMessage catches exceptions internally, so no exception propagated
        agentHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, null, 1L, MessageType.TASK_REQUEST, "테스트"));

        verifyNoInteractions(messageRouter);
    }

    @Test
    @DisplayName("대기 중인 작업이 남아있으면 LLM을 재호출하지 않는다")
    void pendingTasksPreventsLlmCall() {
        ArgumentCaptor<MessageHandler> agentHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        ArgumentCaptor<MessageHandler> orchHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(1L), agentHandlerCaptor.capture()))
                .willReturn(agentSubscription);
        given(messageSubscriber.subscribeToOrchestrator(eq(1L), orchHandlerCaptor.capture()))
                .willReturn(orchestratorSubscription);

        // 2개의 delegate_task 동시 분배
        LlmResponse llmResponse = new LlmResponse("resp-1", null, "tool_use",
                List.of(
                        new LlmToolCall("call-1", "delegate_task", Map.of("agent_id", 2, "task", "작업1")),
                        new LlmToolCall("call-2", "delegate_task", Map.of("agent_id", 3, "task", "작업2"))
                ), null);

        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.of(llmProvider));
        given(llmProvider.sendMessage(any(LlmRequest.class))).willReturn(llmResponse);

        Agent worker2 = Agent.builder().id(3L).name("Worker2").roleType(RoleType.WORKER)
                .role("분석").llmConfig(Map.of("provider", "claude")).build();

        orchestratorService.startOrchestration(1L, orchestrator, Set.of(worker, worker2), completeHandler);
        agentHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, null, 1L, MessageType.TASK_REQUEST, "프롬프트"));

        // 1개 결과만 수신 (아직 1개 대기 중)
        orchHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, 2L, null, MessageType.TASK_RESULT, "결과1"));

        // LLM은 1번만 호출됨 (초기 프롬프트 처리 시)
        verify(llmProvider, times(1)).sendMessage(any(LlmRequest.class));
    }

    @Test
    @DisplayName("유효하지 않은 Agent ID로 delegate_task 시 작업을 분배하지 않는다")
    void invalidAgentIdSkipsDelegation() {
        ArgumentCaptor<MessageHandler> agentHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(1L), agentHandlerCaptor.capture()))
                .willReturn(agentSubscription);
        given(messageSubscriber.subscribeToOrchestrator(eq(1L), any(MessageHandler.class)))
                .willReturn(orchestratorSubscription);

        // 존재하지 않는 Agent ID(999)로 delegate_task
        LlmToolCall invalidDelegate = new LlmToolCall("call-1", "delegate_task",
                Map.of("agent_id", 999, "task", "작업"));
        LlmResponse llmResponse = new LlmResponse("resp-1", null, "tool_use",
                List.of(invalidDelegate), null);

        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.of(llmProvider));
        given(llmProvider.sendMessage(any(LlmRequest.class))).willReturn(llmResponse);

        orchestratorService.startOrchestration(1L, orchestrator, agents, completeHandler);
        agentHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, null, 1L, MessageType.TASK_REQUEST, "테스트"));

        verifyNoInteractions(messageRouter);
    }

    @Test
    @DisplayName("complete_session 후 Orchestration이 정리된다")
    void completeSessionCleansUpOrchestration() {
        ArgumentCaptor<MessageHandler> agentHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(1L), agentHandlerCaptor.capture()))
                .willReturn(agentSubscription);
        given(messageSubscriber.subscribeToOrchestrator(eq(1L), any(MessageHandler.class)))
                .willReturn(orchestratorSubscription);

        LlmToolCall completeCall = new LlmToolCall("call-1", "complete_session",
                Map.of("result", "완료"));
        LlmResponse llmResponse = new LlmResponse("resp-1", null, "tool_use",
                List.of(completeCall), null);

        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.of(llmProvider));
        given(llmProvider.sendMessage(any(LlmRequest.class))).willReturn(llmResponse);

        orchestratorService.startOrchestration(1L, orchestrator, agents, completeHandler);
        agentHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, null, 1L, MessageType.TASK_REQUEST, "테스트"));

        // complete_session 후 구독이 해제됨
        verify(agentSubscription).unsubscribe();
        verify(orchestratorSubscription).unsubscribe();
    }

    @Test
    @DisplayName("complete_session 호출 시 SessionCompleteHandler가 실행된다")
    void completeSessionInvokesHandler() {
        ArgumentCaptor<MessageHandler> agentHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(1L), agentHandlerCaptor.capture()))
                .willReturn(agentSubscription);
        given(messageSubscriber.subscribeToOrchestrator(eq(1L), any(MessageHandler.class)))
                .willReturn(orchestratorSubscription);

        LlmToolCall completeCall = new LlmToolCall("call-1", "complete_session",
                Map.of("result", "최종 결과입니다"));
        LlmResponse llmResponse = new LlmResponse("resp-1", null, "tool_use",
                List.of(completeCall), null);

        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.of(llmProvider));
        given(llmProvider.sendMessage(any(LlmRequest.class))).willReturn(llmResponse);

        orchestratorService.startOrchestration(1L, orchestrator, agents, completeHandler);
        agentHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, null, 1L, MessageType.TASK_REQUEST, "작업 요청"));

        verify(completeHandler).onSessionComplete(1L, "최종 결과입니다");
    }

    @Test
    @DisplayName("delegate_task에 agent_id가 없으면 예외를 잡고 계속한다")
    void delegateTaskMissingAgentIdHandled() {
        ArgumentCaptor<MessageHandler> agentHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(1L), agentHandlerCaptor.capture()))
                .willReturn(agentSubscription);
        given(messageSubscriber.subscribeToOrchestrator(eq(1L), any(MessageHandler.class)))
                .willReturn(orchestratorSubscription);

        // agent_id가 없는 malformed delegate_task
        LlmToolCall malformedDelegate = new LlmToolCall("call-1", "delegate_task",
                Map.of("task", "작업만 있음"));
        LlmResponse llmResponse = new LlmResponse("resp-1", null, "tool_use",
                List.of(malformedDelegate), null);

        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.of(llmProvider));
        given(llmProvider.sendMessage(any(LlmRequest.class))).willReturn(llmResponse);

        orchestratorService.startOrchestration(1L, orchestrator, agents, completeHandler);

        // handleMessage 내부에서 NullPointerException이 catch됨 → 예외 전파 없음
        agentHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, null, 1L, MessageType.TASK_REQUEST, "테스트"));

        verifyNoInteractions(messageRouter);
    }

    @Test
    @DisplayName("complete_session에 result가 없으면 null로 완료 처리된다")
    void completeSessionMissingResult() {
        ArgumentCaptor<MessageHandler> agentHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(1L), agentHandlerCaptor.capture()))
                .willReturn(agentSubscription);
        given(messageSubscriber.subscribeToOrchestrator(eq(1L), any(MessageHandler.class)))
                .willReturn(orchestratorSubscription);

        // result가 없는 complete_session
        LlmToolCall completeCall = new LlmToolCall("call-1", "complete_session", Map.of());
        LlmResponse llmResponse = new LlmResponse("resp-1", null, "tool_use",
                List.of(completeCall), null);

        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.of(llmProvider));
        given(llmProvider.sendMessage(any(LlmRequest.class))).willReturn(llmResponse);

        orchestratorService.startOrchestration(1L, orchestrator, agents, completeHandler);
        agentHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, null, 1L, MessageType.TASK_REQUEST, "테스트"));

        verify(completeHandler).onSessionComplete(1L, null);
    }

    @Test
    @DisplayName("알 수 없는 tool 이름은 무시된다")
    void unknownToolNameIgnored() {
        ArgumentCaptor<MessageHandler> agentHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(1L), agentHandlerCaptor.capture()))
                .willReturn(agentSubscription);
        given(messageSubscriber.subscribeToOrchestrator(eq(1L), any(MessageHandler.class)))
                .willReturn(orchestratorSubscription);

        LlmToolCall unknownCall = new LlmToolCall("call-1", "unknown_tool", Map.of());
        LlmResponse llmResponse = new LlmResponse("resp-1", null, "tool_use",
                List.of(unknownCall), null);

        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.of(llmProvider));
        given(llmProvider.sendMessage(any(LlmRequest.class))).willReturn(llmResponse);

        orchestratorService.startOrchestration(1L, orchestrator, agents, completeHandler);
        agentHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, null, 1L, MessageType.TASK_REQUEST, "테스트"));

        verifyNoInteractions(messageRouter);
        verifyNoInteractions(completeHandler);
    }

    @Test
    @DisplayName("중복 TASK_RESULT 수신 시 카운터 언더플로우를 방지한다")
    void duplicateTaskResultPreventsUnderflow() {
        ArgumentCaptor<MessageHandler> agentHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        ArgumentCaptor<MessageHandler> orchHandlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(1L), agentHandlerCaptor.capture()))
                .willReturn(agentSubscription);
        given(messageSubscriber.subscribeToOrchestrator(eq(1L), orchHandlerCaptor.capture()))
                .willReturn(orchestratorSubscription);

        // 1개의 delegate_task
        LlmToolCall delegateCall = new LlmToolCall("call-1", "delegate_task",
                Map.of("agent_id", 2, "task", "작업"));
        LlmToolCall completeCall = new LlmToolCall("call-2", "complete_session",
                Map.of("result", "완료"));
        LlmResponse firstResponse = new LlmResponse("resp-1", null, "tool_use",
                List.of(delegateCall), null);
        LlmResponse secondResponse = new LlmResponse("resp-2", null, "tool_use",
                List.of(completeCall), null);

        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.of(llmProvider));
        given(llmProvider.sendMessage(any(LlmRequest.class)))
                .willReturn(firstResponse)
                .willReturn(secondResponse);

        orchestratorService.startOrchestration(1L, orchestrator, agents, completeHandler);
        agentHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, null, 1L, MessageType.TASK_REQUEST, "프롬프트"));

        // 정상 결과 1개 수신
        orchHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, 2L, null, MessageType.TASK_RESULT, "결과"));

        // 중복 결과 수신 - LLM이 추가 호출되지 않아야 함
        orchHandlerCaptor.getValue().handle(
                SessionMessage.of(1L, 2L, null, MessageType.TASK_RESULT, "중복 결과"));

        // LLM은 2번만 호출됨 (프롬프트 + 첫 결과), 중복 결과에서는 미호출
        verify(llmProvider, times(2)).sendMessage(any(LlmRequest.class));
    }
}
