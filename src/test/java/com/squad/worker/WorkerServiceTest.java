package com.squad.worker;

import com.squad.agent.domain.Agent;
import com.squad.agent.domain.RoleType;
import com.squad.llm.LlmProvider;
import com.squad.llm.LlmProviderFactory;
import com.squad.llm.model.LlmRequest;
import com.squad.llm.model.LlmResponse;
import com.squad.messaging.MessageHandler;
import com.squad.messaging.MessageRouter;
import com.squad.messaging.MessageSubscriber;
import com.squad.messaging.SessionMessage;
import com.squad.messaging.Subscription;
import com.squad.monitoring.SessionEventPublisher;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkerService 단위 테스트")
class WorkerServiceTest {

    @Mock
    private LlmProviderFactory llmProviderFactory;

    @Mock
    private MessageRouter messageRouter;

    @Mock
    private MessageSubscriber messageSubscriber;

    @Mock
    private LlmProvider llmProvider;

    @Mock
    private SessionEventPublisher sessionEventPublisher;

    @Mock
    private Subscription subscription;

    private WorkerService workerService;

    private Agent worker;

    @BeforeEach
    void setUp() {
        workerService = new WorkerService(llmProviderFactory, messageRouter, messageSubscriber, sessionEventPublisher);

        worker = Agent.builder()
                .id(2L)
                .name("Worker")
                .roleType(RoleType.WORKER)
                .role("코드를 작성합니다")
                .llmConfig(Map.of("provider", "claude", "model", "claude-sonnet-4-20250514"))
                .build();
    }

    @Test
    @DisplayName("startWorker 호출 시 Agent 채널을 구독한다")
    void startWorkerSubscribes() {
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(2L), any(MessageHandler.class)))
                .willReturn(subscription);

        workerService.startWorker(1L, worker);

        verify(messageSubscriber).subscribeToAgent(eq(1L), eq(2L), any(MessageHandler.class));
    }

    @Test
    @DisplayName("stopWorker 호출 시 구독을 해제한다")
    void stopWorkerUnsubscribes() {
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(2L), any(MessageHandler.class)))
                .willReturn(subscription);

        workerService.startWorker(1L, worker);
        workerService.stopWorker(1L, 2L);

        verify(subscription).unsubscribe();
    }

    @Test
    @DisplayName("stopAllWorkers 호출 시 세션의 모든 Worker를 중지한다")
    void stopAllWorkersUnsubscribesAll() {
        Agent worker2 = Agent.builder()
                .id(3L).name("Worker2").roleType(RoleType.WORKER)
                .role("분석").llmConfig(Map.of("provider", "claude")).build();

        Subscription sub2 = mock(Subscription.class);

        given(messageSubscriber.subscribeToAgent(eq(1L), eq(2L), any(MessageHandler.class)))
                .willReturn(subscription);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(3L), any(MessageHandler.class)))
                .willReturn(sub2);

        workerService.startWorker(1L, worker);
        workerService.startWorker(1L, worker2);
        workerService.stopAllWorkers(1L);

        verify(subscription).unsubscribe();
        verify(sub2).unsubscribe();
    }

    @Test
    @DisplayName("TASK_REQUEST 수신 시 LLM을 호출하고 TASK_RESULT를 반환한다")
    void taskRequestCallsLlmAndReturnsResult() {
        ArgumentCaptor<MessageHandler> handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(2L), handlerCaptor.capture()))
                .willReturn(subscription);

        LlmResponse llmResponse = new LlmResponse("resp-1", "작업 결과입니다", "stop",
                List.of(), null);

        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.of(llmProvider));
        given(llmProvider.sendMessage(any(LlmRequest.class))).willReturn(llmResponse);

        workerService.startWorker(1L, worker);
        handlerCaptor.getValue().handle(
                SessionMessage.of(1L, 1L, 2L, MessageType.TASK_REQUEST, "코드를 작성해주세요"));

        ArgumentCaptor<SessionMessage> resultCaptor = ArgumentCaptor.forClass(SessionMessage.class);
        verify(messageRouter).route(resultCaptor.capture());

        SessionMessage result = resultCaptor.getValue();
        assertThat(result.getType()).isEqualTo(MessageType.TASK_RESULT);
        assertThat(result.getFromAgentId()).isEqualTo(2L);
        assertThat(result.getToAgentId()).isNull();
        assertThat(result.getContent()).isEqualTo("작업 결과입니다");
    }

    @Test
    @DisplayName("LLM 호출 실패 시 에러 결과를 Orchestrator에게 반환한다")
    void llmFailureReturnsErrorResult() {
        ArgumentCaptor<MessageHandler> handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(2L), handlerCaptor.capture()))
                .willReturn(subscription);

        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.of(llmProvider));
        given(llmProvider.sendMessage(any(LlmRequest.class)))
                .willThrow(new RuntimeException("LLM 호출 실패"));

        workerService.startWorker(1L, worker);
        handlerCaptor.getValue().handle(
                SessionMessage.of(1L, 1L, 2L, MessageType.TASK_REQUEST, "작업 요청"));

        ArgumentCaptor<SessionMessage> resultCaptor = ArgumentCaptor.forClass(SessionMessage.class);
        verify(messageRouter).route(resultCaptor.capture());

        SessionMessage result = resultCaptor.getValue();
        assertThat(result.getType()).isEqualTo(MessageType.TASK_RESULT);
        assertThat(result.getContent()).contains("[오류]");
        assertThat(result.getContent()).contains("LLM 호출 실패");
    }

    @Test
    @DisplayName("LLM Provider를 찾을 수 없으면 에러 결과를 반환한다")
    void unknownProviderReturnsError() {
        ArgumentCaptor<MessageHandler> handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(2L), handlerCaptor.capture()))
                .willReturn(subscription);

        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.empty());

        workerService.startWorker(1L, worker);
        handlerCaptor.getValue().handle(
                SessionMessage.of(1L, 1L, 2L, MessageType.TASK_REQUEST, "작업 요청"));

        ArgumentCaptor<SessionMessage> resultCaptor = ArgumentCaptor.forClass(SessionMessage.class);
        verify(messageRouter).route(resultCaptor.capture());

        assertThat(resultCaptor.getValue().getType()).isEqualTo(MessageType.TASK_RESULT);
        assertThat(resultCaptor.getValue().getContent()).contains("[오류]");
    }

    @Test
    @DisplayName("TASK_REQUEST 외의 메시지 타입은 무시한다")
    void nonTaskRequestIgnored() {
        ArgumentCaptor<MessageHandler> handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(2L), handlerCaptor.capture()))
                .willReturn(subscription);

        workerService.startWorker(1L, worker);
        handlerCaptor.getValue().handle(
                SessionMessage.of(1L, null, 2L, MessageType.SYSTEM, "시스템 메시지"));

        verifyNoInteractions(messageRouter);
        verifyNoInteractions(llmProviderFactory);
    }

    @Test
    @DisplayName("시스템 프롬프트에 Agent 이름과 역할이 포함된다")
    void systemPromptContainsAgentInfo() {
        String prompt = workerService.buildSystemPrompt(worker);

        assertThat(prompt).contains("Worker");
        assertThat(prompt).contains("코드를 작성합니다");
    }

    @Test
    @DisplayName("LLM 응답이 빈 content일 때 기본 메시지를 반환한다")
    void emptyLlmResponseReturnsDefault() {
        ArgumentCaptor<MessageHandler> handlerCaptor = ArgumentCaptor.forClass(MessageHandler.class);
        given(messageSubscriber.subscribeToAgent(eq(1L), eq(2L), handlerCaptor.capture()))
                .willReturn(subscription);

        LlmResponse llmResponse = new LlmResponse("resp-1", null, "stop", List.of(), null);

        given(llmProviderFactory.getProvider("claude")).willReturn(Optional.of(llmProvider));
        given(llmProvider.sendMessage(any(LlmRequest.class))).willReturn(llmResponse);

        workerService.startWorker(1L, worker);
        handlerCaptor.getValue().handle(
                SessionMessage.of(1L, 1L, 2L, MessageType.TASK_REQUEST, "작업 요청"));

        ArgumentCaptor<SessionMessage> resultCaptor = ArgumentCaptor.forClass(SessionMessage.class);
        verify(messageRouter).route(resultCaptor.capture());

        assertThat(resultCaptor.getValue().getContent()).contains("응답 내용이 없습니다");
    }
}
