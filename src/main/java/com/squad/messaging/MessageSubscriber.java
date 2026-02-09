package com.squad.messaging;

/**
 * 세션 메시지 구독 인터페이스.
 *
 * <p>세션 내 에이전트 간 통신 채널을 구독하는 도메인 포트를 정의한다.
 * 실제 구독 방식(Redis Pub/Sub, Kafka, AWS SNS 등)은 구현체가 결정한다.</p>
 *
 * <p>각 구독 메서드는 {@link Subscription}을 반환하여 호출자가 구독 lifecycle을 관리할 수 있다.</p>
 *
 * @see Subscription
 * @see MessageHandler
 * @see SessionMessage
 */
public interface MessageSubscriber {

    /**
     * 특정 Agent 채널을 구독한다.
     *
     * @param sessionId 세션 ID (필수)
     * @param agentId   Agent ID (필수)
     * @param handler   메시지 수신 핸들러
     * @return 구독 해제에 사용할 {@link Subscription}
     */
    Subscription subscribeToAgent(Long sessionId, Long agentId, MessageHandler handler);

    /**
     * Orchestrator 채널을 구독한다.
     *
     * @param sessionId 세션 ID (필수)
     * @param handler   메시지 수신 핸들러
     * @return 구독 해제에 사용할 {@link Subscription}
     */
    Subscription subscribeToOrchestrator(Long sessionId, MessageHandler handler);

    /**
     * 브로드캐스트 채널을 구독한다.
     *
     * @param sessionId 세션 ID (필수)
     * @param handler   메시지 수신 핸들러
     * @return 구독 해제에 사용할 {@link Subscription}
     */
    Subscription subscribeToBroadcast(Long sessionId, MessageHandler handler);
}
