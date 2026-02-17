package com.squad.cli.websocket;

/**
 * 서버에서 수신하는 세션 이벤트 타입.
 *
 * <p>서버의 {@code com.squad.monitoring.SessionEventType}에 대응한다.</p>
 *
 * @see SessionEventMessage
 */
public enum SessionEventType {

    /** Agent 간 메시지 발생 (TASK_REQUEST, TASK_RESULT 등) */
    MESSAGE,

    /** Agent 상태 변경 (작업 시작, 작업 완료 등) */
    AGENT_STATUS,

    /** 세션 완료 */
    SESSION_COMPLETE
}
