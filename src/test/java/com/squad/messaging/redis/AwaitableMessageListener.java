package com.squad.messaging.redis;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.SubscriptionListener;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 테스트용 Redis 메시지 리스너.
 *
 * <p>{@link SubscriptionListener}를 구현하여 구독 완료 시점을 대기할 수 있고,
 * 수신된 메시지를 {@link CountDownLatch}로 동기화하여 안정적인 테스트를 지원한다.</p>
 */
class AwaitableMessageListener implements MessageListener, SubscriptionListener {

    private final CountDownLatch subscribedLatch = new CountDownLatch(1);
    private final CountDownLatch messageLatch = new CountDownLatch(1);
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final String expectedChannel;

    AwaitableMessageListener(String expectedChannel) {
        this.expectedChannel = expectedChannel;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        receivedBody.set(new String(message.getBody(), StandardCharsets.UTF_8));
        messageLatch.countDown();
    }

    @Override
    public void onChannelSubscribed(byte[] channel, long count) {
        if (expectedChannel.equals(new String(channel, StandardCharsets.UTF_8))) {
            subscribedLatch.countDown();
        }
    }

    @Override
    public void onChannelUnsubscribed(byte[] channel, long count) {
    }

    @Override
    public void onPatternSubscribed(byte[] pattern, long count) {
    }

    @Override
    public void onPatternUnsubscribed(byte[] pattern, long count) {
    }

    /**
     * 채널 구독이 완료될 때까지 대기한다.
     *
     * @param timeout 대기 시간
     * @param unit    시간 단위
     * @return 구독 완료 여부
     */
    boolean awaitSubscribed(long timeout, TimeUnit unit) throws InterruptedException {
        return subscribedLatch.await(timeout, unit);
    }

    /**
     * 메시지 수신까지 대기한다.
     *
     * @param timeout 대기 시간
     * @param unit    시간 단위
     * @return 메시지 수신 여부
     */
    boolean awaitMessage(long timeout, TimeUnit unit) throws InterruptedException {
        return messageLatch.await(timeout, unit);
    }

    /**
     * 수신된 메시지 본문을 반환한다.
     *
     * @return 수신된 메시지 본문 (미수신 시 null)
     */
    String getReceivedBody() {
        return receivedBody.get();
    }
}
