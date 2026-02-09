package com.squad.messaging.redis;

import com.squad.messaging.MessagePublisher;
import com.squad.messaging.SessionMessage;
import com.squad.session.domain.MessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("RedisMessagePublisher 통합 테스트")
class RedisMessagePublisherTest extends RedisTestContainerConfig {

    @Autowired
    private MessagePublisher messagePublisher;

    @Autowired
    private RedisMessageListenerContainer listenerContainer;

    @Test
    @DisplayName("Agent 채널에 메시지를 발행하면 해당 채널에서 수신할 수 있다")
    void sendToAgent() throws InterruptedException {
        Long sessionId = 1L;
        Long agentId = 10L;
        SessionMessage message = SessionMessage.of(sessionId, 2L, agentId,
                MessageType.TASK_REQUEST, "코드를 분석해주세요");

        String channel = RedisChannelConstants.agentChannel(sessionId, agentId);
        String received = publishAndReceive(channel, () -> messagePublisher.sendToAgent(message));

        assertThat(received).contains("코드를 분석해주세요");
    }

    @Test
    @DisplayName("Orchestrator 채널에 메시지를 발행하면 해당 채널에서 수신할 수 있다")
    void sendToOrchestrator() throws InterruptedException {
        Long sessionId = 2L;
        SessionMessage message = SessionMessage.of(sessionId, 5L, null,
                MessageType.TASK_RESULT, "분석 결과입니다");

        String channel = RedisChannelConstants.orchestratorChannel(sessionId);
        String received = publishAndReceive(channel, () -> messagePublisher.sendToOrchestrator(message));

        assertThat(received).contains("분석 결과입니다");
    }

    @Test
    @DisplayName("브로드캐스트 채널에 메시지를 발행하면 해당 채널에서 수신할 수 있다")
    void broadcast() throws InterruptedException {
        Long sessionId = 3L;
        SessionMessage message = SessionMessage.system(sessionId, "세션이 시작되었습니다");

        String channel = RedisChannelConstants.broadcastChannel(sessionId);
        String received = publishAndReceive(channel, () -> messagePublisher.broadcast(message));

        assertThat(received).contains("세션이 시작되었습니다");
    }

    @Test
    @DisplayName("발행된 메시지의 타입 정보가 유지된다")
    void messageTypePreserved() throws InterruptedException {
        Long sessionId = 4L;
        Long agentId = 20L;
        SessionMessage message = SessionMessage.of(sessionId, 1L, agentId,
                MessageType.HELP_REQUEST, "도움이 필요합니다");

        String channel = RedisChannelConstants.agentChannel(sessionId, agentId);
        String received = publishAndReceive(channel, () -> messagePublisher.sendToAgent(message));

        assertThat(received).contains("HELP_REQUEST");
    }

    @Test
    @DisplayName("서로 다른 세션의 메시지는 격리된다")
    void sessionIsolation() throws InterruptedException {
        Long sessionId1 = 100L;
        Long sessionId2 = 200L;
        Long agentId = 1L;

        String listenChannel = RedisChannelConstants.agentChannel(sessionId1, agentId);
        AwaitableMessageListener listener = new AwaitableMessageListener(listenChannel);
        ChannelTopic topic = new ChannelTopic(listenChannel);

        try {
            listenerContainer.addMessageListener(listener, topic);
            assertThat(listener.awaitSubscribed(3, TimeUnit.SECONDS)).isTrue();

            SessionMessage message2 = SessionMessage.of(sessionId2, 2L, agentId,
                    MessageType.TASK_REQUEST, "다른 세션 메시지");
            messagePublisher.sendToAgent(message2);

            boolean received = listener.awaitMessage(1, TimeUnit.SECONDS);

            assertThat(received).isFalse();
            assertThat(listener.getReceivedBody()).isNull();
        } finally {
            listenerContainer.removeMessageListener(listener, topic);
        }
    }

    @Test
    @DisplayName("sendToAgent 호출 시 sessionId가 null이면 예외가 발생한다")
    void sendToAgentWithNullSessionId() {
        SessionMessage message = SessionMessage.of(null, 1L, 2L,
                MessageType.TASK_REQUEST, "테스트");

        assertThatThrownBy(() -> messagePublisher.sendToAgent(message))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sendToAgent 호출 시 toAgentId가 null이면 예외가 발생한다")
    void sendToAgentWithNullToAgentId() {
        SessionMessage message = SessionMessage.of(1L, 1L, null,
                MessageType.TASK_REQUEST, "테스트");

        assertThatThrownBy(() -> messagePublisher.sendToAgent(message))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String publishAndReceive(String channel, Runnable publishAction) throws InterruptedException {
        AwaitableMessageListener listener = new AwaitableMessageListener(channel);
        ChannelTopic topic = new ChannelTopic(channel);

        try {
            listenerContainer.addMessageListener(listener, topic);
            assertThat(listener.awaitSubscribed(3, TimeUnit.SECONDS)).isTrue();

            publishAction.run();

            assertThat(listener.awaitMessage(5, TimeUnit.SECONDS)).isTrue();
            return listener.getReceivedBody();
        } finally {
            listenerContainer.removeMessageListener(listener, topic);
        }
    }
}
