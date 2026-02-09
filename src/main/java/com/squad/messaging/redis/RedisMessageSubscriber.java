package com.squad.messaging.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squad.messaging.MessageHandler;
import com.squad.messaging.MessageSubscriber;
import com.squad.messaging.SessionMessage;
import com.squad.messaging.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis Pub/Sub 기반 {@link MessageSubscriber} 구현체.
 *
 * <p>{@link RedisMessageListenerContainer}를 사용하여 채널을 구독하고,
 * {@link ObjectMapper}로 메시지를 역직렬화하여 {@link MessageHandler}에 전달한다.</p>
 *
 * <p>{@link RedisSerializer}로 1차 역직렬화한 결과가 {@link SessionMessage}가 아닌 경우
 * (예: private 생성자로 인한 {@link java.util.LinkedHashMap} fallback),
 * {@link ObjectMapper#convertValue}를 사용하여 타입 변환을 수행한다.</p>
 *
 * <p>반환되는 {@link Subscription}은 멱등한 구독 해제를 지원하며,
 * {@link java.util.concurrent.atomic.AtomicBoolean}으로 스레드 안전성을 보장한다.</p>
 *
 * <p>{@code squad.messaging.provider=redis}일 때 활성화된다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "squad.messaging.provider", havingValue = "redis")
public class RedisMessageSubscriber implements MessageSubscriber {

    private final RedisMessageListenerContainer listenerContainer;
    private final RedisSerializer<Object> messageSerializer;
    private final ObjectMapper objectMapper;

    @Override
    public Subscription subscribeToAgent(Long sessionId, Long agentId, MessageHandler handler) {
        Assert.notNull(sessionId, "sessionId는 필수입니다");
        Assert.notNull(agentId, "agentId는 필수입니다");
        Assert.notNull(handler, "handler는 필수입니다");

        String channel = RedisChannelConstants.agentChannel(sessionId, agentId);
        return subscribe(channel, handler);
    }

    @Override
    public Subscription subscribeToOrchestrator(Long sessionId, MessageHandler handler) {
        Assert.notNull(sessionId, "sessionId는 필수입니다");
        Assert.notNull(handler, "handler는 필수입니다");

        String channel = RedisChannelConstants.orchestratorChannel(sessionId);
        return subscribe(channel, handler);
    }

    @Override
    public Subscription subscribeToBroadcast(Long sessionId, MessageHandler handler) {
        Assert.notNull(sessionId, "sessionId는 필수입니다");
        Assert.notNull(handler, "handler는 필수입니다");

        String channel = RedisChannelConstants.broadcastChannel(sessionId);
        return subscribe(channel, handler);
    }

    private Subscription subscribe(String channel, MessageHandler handler) {
        MessageListener listener = createListener(channel, handler);
        ChannelTopic topic = new ChannelTopic(channel);

        listenerContainer.addMessageListener(listener, topic);
        log.debug("채널 구독: channel={}", channel);

        return new RedisSubscription(listenerContainer, listener, topic, channel);
    }

    private MessageListener createListener(String channel, MessageHandler handler) {
        return (Message message, byte[] pattern) -> {
            try {
                Object deserialized = messageSerializer.deserialize(message.getBody());
                if (deserialized == null) {
                    log.warn("역직렬화 결과가 null입니다: channel={}", channel);
                    return;
                }
                SessionMessage sessionMessage = toSessionMessage(deserialized);
                handler.handle(sessionMessage);
            } catch (Exception e) {
                log.error("메시지 처리 실패: channel={}", channel, e);
            }
        };
    }

    private SessionMessage toSessionMessage(Object deserialized) {
        if (deserialized instanceof SessionMessage sessionMessage) {
            return sessionMessage;
        }
        return objectMapper.convertValue(deserialized, SessionMessage.class);
    }

    /**
     * Redis 채널 구독을 나타내는 내부 {@link Subscription} 구현체.
     *
     * <p>{@link AtomicBoolean}을 사용하여 멱등한 구독 해제를 보장한다.</p>
     */
    private static class RedisSubscription implements Subscription {

        private final RedisMessageListenerContainer listenerContainer;
        private final MessageListener listener;
        private final ChannelTopic topic;
        private final String channel;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private RedisSubscription(RedisMessageListenerContainer listenerContainer,
                                  MessageListener listener,
                                  ChannelTopic topic,
                                  String channel) {
            this.listenerContainer = listenerContainer;
            this.listener = listener;
            this.topic = topic;
            this.channel = channel;
        }

        @Override
        public void unsubscribe() {
            if (active.compareAndSet(true, false)) {
                try {
                    listenerContainer.removeMessageListener(listener, topic);
                    log.debug("채널 구독 해제: channel={}", channel);
                } catch (Exception e) {
                    active.set(true);
                    throw e;
                }
            }
        }
    }
}
