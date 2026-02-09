package com.squad.messaging.redis;

import com.squad.messaging.MessagePublisher;
import com.squad.messaging.SessionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * Redis Pub/Sub 기반 {@link MessagePublisher} 구현체.
 *
 * <p>{@link RedisChannelConstants}에 정의된 채널 규칙에 따라
 * {@link RedisTemplate#convertAndSend}를 사용하여 메시지를 발행한다.</p>
 *
 * <p>{@code squad.messaging.provider=redis}일 때 활성화된다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "squad.messaging.provider", havingValue = "redis")
public class RedisMessagePublisher implements MessagePublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void sendToAgent(SessionMessage message) {
        Assert.notNull(message.getSessionId(), "sessionId는 필수입니다");
        Assert.notNull(message.getToAgentId(), "toAgentId는 필수입니다");

        String channel = RedisChannelConstants.agentChannel(message.getSessionId(), message.getToAgentId());
        publish(channel, message);
    }

    @Override
    public void sendToOrchestrator(SessionMessage message) {
        Assert.notNull(message.getSessionId(), "sessionId는 필수입니다");

        String channel = RedisChannelConstants.orchestratorChannel(message.getSessionId());
        publish(channel, message);
    }

    @Override
    public void broadcast(SessionMessage message) {
        Assert.notNull(message.getSessionId(), "sessionId는 필수입니다");

        String channel = RedisChannelConstants.broadcastChannel(message.getSessionId());
        publish(channel, message);
    }

    private void publish(String channel, SessionMessage message) {
        log.debug("메시지 발행: channel={}, type={}, from={}, to={}",
                channel, message.getType(), message.getFromAgentId(), message.getToAgentId());
        redisTemplate.convertAndSend(channel, message);
    }
}
