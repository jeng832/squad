package com.squad.agent.domain;

/**
 * 에이전트의 역할 유형을 정의하는 열거형.
 *
 * <p>데이터베이스 ENUM 값은 소문자로 저장되며, Agent 엔티티 내의
 * {@code RoleTypeConverter}를 통해 자동 변환됩니다.</p>
 */
public enum RoleType {
    ORCHESTRATOR,
    WORKER,
    ANALYST,
    SCRIBE,
    CUSTOM
}
