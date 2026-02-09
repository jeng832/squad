package com.squad.messaging;

/**
 * 메시지 타입에 따라 적절한 채널로 라우팅하는 인터페이스.
 *
 * <p>{@link SessionMessage}의 {@link com.squad.session.domain.MessageType}을 기준으로
 * Orchestrator, Agent, 또는 브로드캐스트 채널로 메시지를 전달한다.</p>
 *
 * <p>라우팅 규칙:</p>
 * <ul>
 *   <li>{@code TASK_REQUEST}, {@code HELP_RESPONSE} → Agent 채널 (toAgentId 필수)</li>
 *   <li>{@code TASK_RESULT}, {@code HELP_REQUEST} → Orchestrator 채널</li>
 *   <li>{@code SYSTEM} → 브로드캐스트 채널</li>
 * </ul>
 *
 * @see MessagePublisher
 * @see SessionMessage
 */
public interface MessageRouter {

    /**
     * 메시지 타입에 따라 적절한 채널로 라우팅한다.
     *
     * @param message 라우팅할 메시지
     * @throws IllegalArgumentException 라우팅에 필요한 필드가 누락된 경우
     */
    void route(SessionMessage message);
}
