package com.squad.messaging;

import com.squad.messaging.config.RedisTestContainerConfig;
import com.squad.session.domain.MessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("MessagePublisher 통합 테스트")
class MessagePublisherTest extends RedisTestContainerConfig {

    @Autowired
    private MessagePublisher messagePublisher;

    @Autowired
    private RedisMessageListenerContainer listenerContainer;

    @Autowired
    private RedisSerializer<Object> messageSerializer;

    @Test
    @DisplayName("Agent 채널에 메시지를 발행하면 해당 채널에서 수신할 수 있다")
    void sendToAgent() throws InterruptedException {
        Long sessionId = 1L;
        Long agentId = 10L;
        SessionMessage message = SessionMessage.of(sessionId, 2L, agentId,
                MessageType.TASK_REQUEST, "코드를 분석해주세요");

        String received = publishAndReceive(
                RedisChannelConstants.agentChannel(sessionId, agentId), message);

        assertThat(received).contains("코드를 분석해주세요");
    }

    @Test
    @DisplayName("Orchestrator 채널에 메시지를 발행하면 해당 채널에서 수신할 수 있다")
    void sendToOrchestrator() throws InterruptedException {
        Long sessionId = 2L;
        SessionMessage message = SessionMessage.of(sessionId, 5L, null,
                MessageType.TASK_RESULT, "분석 결과입니다");

        String received = publishAndReceive(
                RedisChannelConstants.orchestratorChannel(sessionId), message);

        assertThat(received).contains("분석 결과입니다");
    }

    @Test
    @DisplayName("브로드캐스트 채널에 메시지를 발행하면 해당 채널에서 수신할 수 있다")
    void broadcast() throws InterruptedException {
        Long sessionId = 3L;
        SessionMessage message = SessionMessage.system(sessionId, "세션이 시작되었습니다");

        String received = publishAndReceive(
                RedisChannelConstants.broadcastChannel(sessionId), message);

        assertThat(received).contains("세션이 시작되었습니다");
    }

    @Test
    @DisplayName("발행된 메시지의 타입 정보가 유지된다")
    void messageTypePreserved() throws InterruptedException {
        Long sessionId = 4L;
        Long agentId = 20L;
        SessionMessage message = SessionMessage.of(sessionId, 1L, agentId,
                MessageType.HELP_REQUEST, "도움이 필요합니다");

        String received = publishAndReceive(
                RedisChannelConstants.agentChannel(sessionId, agentId), message);

        assertThat(received).contains("HELP_REQUEST");
    }

    @Test
    @DisplayName("서로 다른 세션의 메시지는 격리된다")
    void sessionIsolation() throws InterruptedException {
        Long sessionId1 = 100L;
        Long sessionId2 = 200L;
        Long agentId = 1L;

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();

        String listenChannel = RedisChannelConstants.agentChannel(sessionId1, agentId);
        MessageListener listener = (msg, pattern) -> {
            received.set(new String(msg.getBody()));
            latch.countDown();
        };

        ChannelTopic topic = new ChannelTopic(listenChannel);
        listenerContainer.addMessageListener(listener, topic);
        Thread.sleep(200);

        SessionMessage message2 = SessionMessage.of(sessionId2, 2L, agentId,
                MessageType.TASK_REQUEST, "다른 세션 메시지");
        messagePublisher.sendToAgent(sessionId2, agentId, message2);

        boolean completed = latch.await(1, TimeUnit.SECONDS);

        listenerContainer.removeMessageListener(listener, topic);

        assertThat(completed).isFalse();
        assertThat(received.get()).isNull();
    }

    private String publishAndReceive(String channel, SessionMessage message) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();

        MessageListener listener = (msg, pattern) -> {
            received.set(new String(msg.getBody()));
            latch.countDown();
        };

        ChannelTopic topic = new ChannelTopic(channel);
        listenerContainer.addMessageListener(listener, topic);
        Thread.sleep(200);

        if (channel.endsWith(":orchestrator")) {
            messagePublisher.sendToOrchestrator(message.getSessionId(), message);
        } else if (channel.endsWith(":broadcast")) {
            messagePublisher.broadcast(message.getSessionId(), message);
        } else {
            messagePublisher.sendToAgent(message.getSessionId(), message.getToAgentId(), message);
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        listenerContainer.removeMessageListener(listener, topic);

        assertThat(completed).isTrue();
        return received.get();
    }
}