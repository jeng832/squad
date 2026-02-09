package com.squad.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis Pub/Sub 기반 메시지 발행기.
 *
 * <p>세션 내 에이전트 간 통신을 위해 {@link RedisChannelConstants}에 정의된
 * 채널 규칙에 따라 메시지를 발행한다.</p>
 *
 * @see RedisChannelConstants
 * @see SessionMessage
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessagePublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 특정 Agent 채널에 메시지를 발행한다.
     *
     * @param sessionId 세션 ID
     * @param agentId   수신 Agent ID
     * @param message   발행할 메시지
     */
    public void sendToAgent(Long sessionId, Long agentId, SessionMessage message) {
        String channel = RedisChannelConstants.agentChannel(sessionId, agentId);
        publish(channel, message);
    }

    /**
     * Orchestrator 채널에 메시지를 발행한다.
     *
     * @param sessionId 세션 ID
     * @param message   발행할 메시지
     */
    public void sendToOrchestrator(Long sessionId, SessionMessage message) {
        String channel = RedisChannelConstants.orchestratorChannel(sessionId);
        publish(channel, message);
    }

    /**
     * 브로드캐스트 채널에 메시지를 발행한다.
     *
     * <p>세션에 참여하는 모든 에이전트에게 메시지가 전달된다.</p>
     *
     * @param sessionId 세션 ID
     * @param message   발행할 메시지
     */
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