package com.squad.monitoring;

/**
 * WebSocket을 통해 클라이언트에게 전송하는 세션 이벤트 타입.
 *
 * <p>각 이벤트 타입은 세션 진행 중 발생하는 주요 상태 변화를 나타낸다.</p>
 *
 * @see SessionEvent
 */
public enum SessionEventType {

    /** Agent 간 메시지 발생 (TASK_REQUEST, TASK_RESULT 등) */
    MESSAGE,

    /** Agent 상태 변경 (작업 시작, 작업 완료 등) */
    AGENT_STATUS,

    /** 세션 완료 */
    SESSION_COMPLETE
}
