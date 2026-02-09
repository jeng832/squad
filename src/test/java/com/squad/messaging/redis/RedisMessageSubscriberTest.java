package com.squad.messaging.redis;

import com.squad.messaging.MessagePublisher;
import com.squad.messaging.MessageSubscriber;
import com.squad.messaging.SessionMessage;
import com.squad.messaging.Subscription;
import com.squad.session.domain.MessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest
@DisplayName("RedisMessageSubscriber 통합 테스트")
class RedisMessageSubscriberTest extends RedisTestContainerConfig {

    @Autowired
    private MessageSubscriber messageSubscriber;

    @Autowired
    private MessagePublisher messagePublisher;

    @Test
    @DisplayName("Agent 채널을 구독하면 해당 채널의 메시지를 수신할 수 있다")
    void subscribeToAgent() throws InterruptedException {
        Long sessionId = 1L;
        Long agentId = 10L;
        AtomicReference<SessionMessage> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Subscription subscription = messageSubscriber.subscribeToAgent(sessionId, agentId, message -> {
            received.set(message);
            latch.countDown();
        });

        try {
            publishUntilReceived(
                    () -> messagePublisher.sendToAgent(
                            SessionMessage.of(sessionId, 2L, agentId, MessageType.TASK_REQUEST, "에이전트 메시지")),
                    latch);

            assertThat(received.get().getContent()).isEqualTo("에이전트 메시지");
            assertThat(received.get().getType()).isEqualTo(MessageType.TASK_REQUEST);
        } finally {
            subscription.unsubscribe();
        }
    }

    @Test
    @DisplayName("Orchestrator 채널을 구독하면 해당 채널의 메시지를 수신할 수 있다")
    void subscribeToOrchestrator() throws InterruptedException {
        Long sessionId = 2L;
        AtomicReference<SessionMessage> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Subscription subscription = messageSubscriber.subscribeToOrchestrator(sessionId, message -> {
            received.set(message);
            latch.countDown();
        });

        try {
            publishUntilReceived(
                    () -> messagePublisher.sendToOrchestrator(
                            SessionMessage.of(sessionId, 5L, null, MessageType.TASK_RESULT, "오케스트레이터 메시지")),
                    latch);

            assertThat(received.get().getContent()).isEqualTo("오케스트레이터 메시지");
        } finally {
            subscription.unsubscribe();
        }
    }

    @Test
    @DisplayName("브로드캐스트 채널을 구독하면 해당 채널의 메시지를 수신할 수 있다")
    void subscribeToBroadcast() throws InterruptedException {
        Long sessionId = 3L;
        AtomicReference<SessionMessage> received = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Subscription subscription = messageSubscriber.subscribeToBroadcast(sessionId, message -> {
            received.set(message);
            latch.countDown();
        });

        try {
            publishUntilReceived(
                    () -> messagePublisher.broadcast(SessionMessage.system(sessionId, "브로드캐스트 메시지")),
                    latch);

            assertThat(received.get().getContent()).isEqualTo("브로드캐스트 메시지");
            assertThat(received.get().getType()).isEqualTo(MessageType.SYSTEM);
        } finally {
            subscription.unsubscribe();
        }
    }

    @Test
    @DisplayName("구독 해제 후에는 메시지를 수신하지 않는다")
    void unsubscribeStopsReceiving() throws InterruptedException {
        Long sessionId = 4L;
        Long agentId = 20L;
        CountDownLatch readyLatch = new CountDownLatch(1);
        CountDownLatch afterUnsubscribeLatch = new CountDownLatch(1);

        Subscription subscription = messageSubscriber.subscribeToAgent(sessionId, agentId, message -> {
            if (readyLatch.getCount() > 0) {
                readyLatch.countDown();
            } else {
                afterUnsubscribeLatch.countDown();
            }
        });

        try {
            publishUntilReceived(
                    () -> messagePublisher.sendToAgent(
                            SessionMessage.of(sessionId, 1L, agentId, MessageType.SYSTEM, "준비 확인")),
                    readyLatch);
        } finally {
            subscription.unsubscribe();
        }

        messagePublisher.sendToAgent(
                SessionMessage.of(sessionId, 1L, agentId, MessageType.TASK_REQUEST, "해제 후 메시지"));

        boolean received = afterUnsubscribeLatch.await(1, TimeUnit.SECONDS);
        assertThat(received).isFalse();
    }

    @Test
    @DisplayName("구독 해제를 여러 번 호출해도 예외가 발생하지 않는다")
    void unsubscribeIsIdempotent() throws InterruptedException {
        Long sessionId = 5L;
        Long agentId = 30L;
        CountDownLatch latch = new CountDownLatch(1);

        Subscription subscription = messageSubscriber.subscribeToAgent(sessionId, agentId, message -> latch.countDown());

        publishUntilReceived(
                () -> messagePublisher.sendToAgent(
                        SessionMessage.of(sessionId, 1L, agentId, MessageType.SYSTEM, "준비 확인")),
                latch);

        subscription.unsubscribe();
        subscription.unsubscribe();
        subscription.unsubscribe();
    }

    @Test
    @DisplayName("서로 다른 세션의 메시지는 격리된다")
    void sessionIsolation() throws InterruptedException {
        Long sessionId1 = 100L;
        Long sessionId2 = 200L;
        Long agentId = 1L;
        CountDownLatch readyLatch = new CountDownLatch(1);
        CountDownLatch isolationLatch = new CountDownLatch(1);

        Subscription subscription = messageSubscriber.subscribeToAgent(sessionId1, agentId, message -> {
            if (readyLatch.getCount() > 0) {
                readyLatch.countDown();
            } else {
                isolationLatch.countDown();
            }
        });

        try {
            publishUntilReceived(
                    () -> messagePublisher.sendToAgent(
                            SessionMessage.of(sessionId1, 2L, agentId, MessageType.SYSTEM, "준비 확인")),
                    readyLatch);

            messagePublisher.sendToAgent(
                    SessionMessage.of(sessionId2, 2L, agentId, MessageType.TASK_REQUEST, "다른 세션 메시지"));

            boolean received = isolationLatch.await(1, TimeUnit.SECONDS);
            assertThat(received).isFalse();
        } finally {
            subscription.unsubscribe();
        }
    }

    @Test
    @DisplayName("subscribeToAgent 호출 시 sessionId가 null이면 예외가 발생한다")
    void subscribeToAgentWithNullSessionId() {
        assertThatThrownBy(() -> messageSubscriber.subscribeToAgent(null, 1L, message -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("subscribeToAgent 호출 시 agentId가 null이면 예외가 발생한다")
    void subscribeToAgentWithNullAgentId() {
        assertThatThrownBy(() -> messageSubscriber.subscribeToAgent(1L, null, message -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("subscribeToAgent 호출 시 handler가 null이면 예외가 발생한다")
    void subscribeToAgentWithNullHandler() {
        assertThatThrownBy(() -> messageSubscriber.subscribeToAgent(1L, 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("subscribeToOrchestrator 호출 시 sessionId가 null이면 예외가 발생한다")
    void subscribeToOrchestratorWithNullSessionId() {
        assertThatThrownBy(() -> messageSubscriber.subscribeToOrchestrator(null, message -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("subscribeToOrchestrator 호출 시 handler가 null이면 예외가 발생한다")
    void subscribeToOrchestratorWithNullHandler() {
        assertThatThrownBy(() -> messageSubscriber.subscribeToOrchestrator(1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("subscribeToBroadcast 호출 시 sessionId가 null이면 예외가 발생한다")
    void subscribeToBroadcastWithNullSessionId() {
        assertThatThrownBy(() -> messageSubscriber.subscribeToBroadcast(null, message -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("subscribeToBroadcast 호출 시 handler가 null이면 예외가 발생한다")
    void subscribeToBroadcastWithNullHandler() {
        assertThatThrownBy(() -> messageSubscriber.subscribeToBroadcast(1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 구독이 실제로 준비될 때까지 메시지를 반복 발행하여 수신을 확인한다.
     *
     * <p>Thread.sleep 기반 대기 대신 결정론적 방식으로 구독 준비 상태를 검증한다.</p>
     *
     * @param publishAction 메시지 발행 액션
     * @param latch         메시지 수신 시 카운트다운될 래치
     */
    private void publishUntilReceived(Runnable publishAction, CountDownLatch latch) throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            publishAction.run();
            if (latch.await(500, TimeUnit.MILLISECONDS)) {
                return;
            }
        }
        fail("메시지 수신 타임아웃");
    }
}
