package com.squad.messaging.redis;

import com.squad.messaging.MessagePublisher;
import com.squad.messaging.SessionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis Pub/Sub 기반 {@link MessagePublisher} 구현체.
 *
 * <p>{@link RedisChannelConstants}에 정의된 채널 규칙에 따라
 * {@link RedisTemplate#convertAndSend}를 사용하여 메시지를 발행한다.</p>
 *
 * <p>{@code squad.messaging.provider=redis}일 때 활성화된다.</p>
 *
 * @see RedisChannelConstants
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "squad.messaging.provider", havingValue = "redis", matchIfMissing = true)
public class RedisMessagePublisher implements MessagePublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void sendToAgent(Long sessionId, Long agentId, SessionMessage message) {
        String channel = RedisChannelConstants.agentChannel(sessionId, agentId);
        publish(channel, message);
    }

    @Override
    public void sendToOrchestrator(Long sessionId, SessionMessage message) {
        String channel = RedisChannelConstants.orchestratorChannel(sessionId);
        publish(channel, message);
    }

    @Override
    public void broadcast(Long sessionId, SessionMessage message) {
        String channel = RedisChannelConstants.broadcastChannel(sessionId);
        publish(channel, message);
    }

    private void publish(String channel, SessionMessage message) {
        log.debug("메시지 발행: channel={}, type={}, from={}, to={}",
                channel, message.getType(), message.getFromAgentId(), message.getToAgentId());
        redisTemplate.convertAndSend(channel, message);
    }
}
