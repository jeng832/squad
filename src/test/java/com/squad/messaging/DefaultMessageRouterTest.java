package com.squad.messaging;

import com.squad.session.domain.MessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultMessageRouter 단위 테스트")
class DefaultMessageRouterTest {

    @Mock
    private MessagePublisher messagePublisher;

    @InjectMocks
    private DefaultMessageRouter messageRouter;

    @Test
    @DisplayName("TASK_REQUEST 메시지는 Agent 채널로 라우팅된다")
    void routeTaskRequest() {
        SessionMessage message = SessionMessage.of(1L, 10L, 20L,
                MessageType.TASK_REQUEST, "작업을 수행해주세요");

        messageRouter.route(message);

        verify(messagePublisher).sendToAgent(message);
    }

    @Test
    @DisplayName("TASK_RESULT 메시지는 Orchestrator 채널로 라우팅된다")
    void routeTaskResult() {
        SessionMessage message = SessionMessage.of(1L, 20L, null,
                MessageType.TASK_RESULT, "작업 결과입니다");

        messageRouter.route(message);

        verify(messagePublisher).sendToOrchestrator(message);
    }

    @Test
    @DisplayName("HELP_REQUEST 메시지는 Orchestrator 채널로 라우팅된다")
    void routeHelpRequest() {
        SessionMessage message = SessionMessage.of(1L, 20L, null,
                MessageType.HELP_REQUEST, "도움이 필요합니다");

        messageRouter.route(message);

        verify(messagePublisher).sendToOrchestrator(message);
    }

    @Test
    @DisplayName("HELP_RESPONSE 메시지는 Agent 채널로 라우팅된다")
    void routeHelpResponse() {
        SessionMessage message = SessionMessage.of(1L, 10L, 20L,
                MessageType.HELP_RESPONSE, "도움 응답입니다");

        messageRouter.route(message);

        verify(messagePublisher).sendToAgent(message);
    }

    @Test
    @DisplayName("SYSTEM 메시지는 브로드캐스트 채널로 라우팅된다")
    void routeSystem() {
        SessionMessage message = SessionMessage.system(1L, "세션이 시작되었습니다");

        messageRouter.route(message);

        verify(messagePublisher).broadcast(message);
    }

    @Test
    @DisplayName("TASK_REQUEST 메시지에 toAgentId가 없으면 예외가 발생한다")
    void taskRequestWithoutToAgentId() {
        SessionMessage message = SessionMessage.of(1L, 10L, null,
                MessageType.TASK_REQUEST, "작업 요청");

        assertThatThrownBy(() -> messageRouter.route(message))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(messagePublisher);
    }

    @Test
    @DisplayName("HELP_RESPONSE 메시지에 toAgentId가 없으면 예외가 발생한다")
    void helpResponseWithoutToAgentId() {
        SessionMessage message = SessionMessage.of(1L, 10L, null,
                MessageType.HELP_RESPONSE, "도움 응답");

        assertThatThrownBy(() -> messageRouter.route(message))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(messagePublisher);
    }

    @Test
    @DisplayName("message가 null이면 예외가 발생한다")
    void routeWithNullMessage() {
        assertThatThrownBy(() -> messageRouter.route(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(messagePublisher);
    }

    @Test
    @DisplayName("sessionId가 null이면 예외가 발생한다")
    void routeWithNullSessionId() {
        SessionMessage message = SessionMessage.of(null, 10L, 20L,
                MessageType.TASK_REQUEST, "테스트");

        assertThatThrownBy(() -> messageRouter.route(message))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(messagePublisher);
    }

    @Test
    @DisplayName("메시지 타입이 null이면 예외가 발생한다")
    void routeWithNullType() {
        SessionMessage message = SessionMessage.of(1L, 10L, 20L,
                null, "테스트");

        assertThatThrownBy(() -> messageRouter.route(message))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(messagePublisher);
    }
}
