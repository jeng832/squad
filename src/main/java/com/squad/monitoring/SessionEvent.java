package com.squad.monitoring;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * WebSocket을 통해 클라이언트에게 전송하는 세션 이벤트 DTO.
 *
 * <p>세션 진행 중 발생하는 메시지, Agent 상태 변경, 세션 완료 등의
 * 이벤트를 클라이언트에게 실시간으로 전달하기 위한 공통 포맷이다.</p>
 *
 * <p>이벤트는 STOMP를 통해 {@code /topic/sessions/{sessionId}} 채널로 발행된다.</p>
 *
 * @see SessionEventType
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SessionEvent {

    private Long sessionId;
    private SessionEventType type;
    private Map<String, Object> payload;
    private LocalDateTime timestamp;

    /**
     * 세션 이벤트를 생성한다.
     *
     * @param sessionId 세션 ID
     * @param type      이벤트 타입
     * @param payload   이벤트 상세 데이터
     * @return 세션 이벤트
     */
    public static SessionEvent of(Long sessionId, SessionEventType type, Map<String, Object> payload) {
        return new SessionEvent(sessionId, type, payload, LocalDateTime.now());
    }
}
