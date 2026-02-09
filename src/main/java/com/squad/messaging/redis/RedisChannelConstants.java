package com.squad.messaging.redis;

/**
 * Redis Pub/Sub 채널 네이밍 규칙을 정의하는 유틸리티 클래스.
 *
 * <p>Redis 구현체 내부에서만 사용되며, 채널 구조는 다음과 같다:</p>
 * <ul>
 *   <li>{@code session:{sessionId}:orchestrator} - Orchestrator 전용 채널</li>
 *   <li>{@code session:{sessionId}:agent:{agentId}} - 개별 Agent 채널</li>
 *   <li>{@code session:{sessionId}:broadcast} - 전체 브로드캐스트 채널</li>
 * </ul>
 */
final class RedisChannelConstants {

    private static final String SESSION_PREFIX = "session:";
    private static final String ORCHESTRATOR_SUFFIX = ":orchestrator";
    private static final String AGENT_INFIX = ":agent:";
    private static final String BROADCAST_SUFFIX = ":broadcast";

    private RedisChannelConstants() {
    }

    /**
     * Orchestrator 전용 채널명을 생성한다.
     *
     * @param sessionId 세션 ID
     * @return {@code session:{sessionId}:orchestrator} 형식의 채널명
     */
    static String orchestratorChannel(Long sessionId) {
        return SESSION_PREFIX + sessionId + ORCHESTRATOR_SUFFIX;
    }

    /**
     * 개별 Agent 채널명을 생성한다.
     *
     * @param sessionId 세션 ID
     * @param agentId   Agent ID
     * @return {@code session:{sessionId}:agent:{agentId}} 형식의 채널명
     */
    static String agentChannel(Long sessionId, Long agentId) {
        return SESSION_PREFIX + sessionId + AGENT_INFIX + agentId;
    }

    /**
     * 브로드캐스트 채널명을 생성한다.
     *
     * @param sessionId 세션 ID
     * @return {@code session:{sessionId}:broadcast} 형식의 채널명
     */
    static String broadcastChannel(Long sessionId) {
        return SESSION_PREFIX + sessionId + BROADCAST_SUFFIX;
    }

    /**
     * 세션에 속한 모든 채널을 구독하기 위한 패턴을 생성한다.
     *
     * @param sessionId 세션 ID
     * @return {@code session:{sessionId}:*} 형식의 패턴
     */
    static String sessionPattern(Long sessionId) {
        return SESSION_PREFIX + sessionId + ":*";
    }
}
