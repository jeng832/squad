package com.squad.messaging;

/**
 * 세션 메시지 발행 인터페이스.
 *
 * <p>세션 내 에이전트 간 통신을 위해 메시지를 발행하는 도메인 포트를 정의한다.
 * 실제 전송 방식(Redis Pub/Sub, Kafka, AWS SNS 등)은 구현체가 결정한다.</p>
 *
 * @see SessionMessage
 */
public interface MessagePublisher {

    /**
     * 특정 Agent에게 메시지를 발행한다.
     *
     * @param sessionId 세션 ID
     * @param agentId   수신 Agent ID
     * @param message   발행할 메시지
     */
    void sendToAgent(Long sessionId, Long agentId, SessionMessage message);

    /**
     * Orchestrator에게 메시지를 발행한다.
     *
     * @param sessionId 세션 ID
     * @param message   발행할 메시지
     */
    void sendToOrchestrator(Long sessionId, SessionMessage message);

    /**
     * 세션에 참여하는 모든 에이전트에게 메시지를 발행한다.
     *
     * @param sessionId 세션 ID
     * @param message   발행할 메시지
     */
    void broadcast(Long sessionId, SessionMessage message);
}
