package com.squad.messaging;

/**
 * 세션 메시지 발행 인터페이스.
 *
 * <p>세션 내 에이전트 간 통신을 위해 메시지를 발행하는 도메인 포트를 정의한다.
 * 실제 전송 방식(Redis Pub/Sub, Kafka, AWS SNS 등)은 구현체가 결정한다.</p>
 *
 * <p>각 메서드는 {@link SessionMessage}에 포함된 라우팅 정보(sessionId, toAgentId 등)를
 * 단일 소스로 사용하여 메시지를 발행한다.</p>
 *
 * @see SessionMessage
 */
public interface MessagePublisher {

    /**
     * 특정 Agent에게 메시지를 발행한다.
     *
     * <p>{@link SessionMessage#getSessionId()}와 {@link SessionMessage#getToAgentId()}가
     * 반드시 존재해야 한다.</p>
     *
     * @param message 발행할 메시지 (sessionId, toAgentId 필수)
     */
    void sendToAgent(SessionMessage message);

    /**
     * Orchestrator에게 메시지를 발행한다.
     *
     * <p>{@link SessionMessage#getSessionId()}가 반드시 존재해야 한다.</p>
     *
     * @param message 발행할 메시지 (sessionId 필수)
     */
    void sendToOrchestrator(SessionMessage message);

    /**
     * 세션에 참여하는 모든 에이전트에게 메시지를 발행한다.
     *
     * <p>{@link SessionMessage#getSessionId()}가 반드시 존재해야 한다.</p>
     *
     * @param message 발행할 메시지 (sessionId 필수)
     */
    void broadcast(SessionMessage message);
}
