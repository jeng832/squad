package com.squad.cli.websocket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 서버 WebSocket에서 수신하는 세션 이벤트 메시지.
 *
 * <p>서버의 {@code com.squad.monitoring.SessionEvent}에 대응하는 클라이언트 DTO이다.
 * STOMP를 통해 {@code /topic/sessions/{sessionId}}에서 수신한다.</p>
 *
 * <p>Jackson 역직렬화를 위해 기본 생성자와 getter를 제공한다.</p>
 *
 * @see SessionEventType
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionEventMessage {

    private Long sessionId;
    private SessionEventType type;
    private Map<String, Object> payload;
    private LocalDateTime timestamp;

    /** Jackson 역직렬화용 기본 생성자. */
    public SessionEventMessage() {
    }

    private SessionEventMessage(Long sessionId, SessionEventType type,
                                Map<String, Object> payload, LocalDateTime timestamp) {
        this.sessionId = sessionId;
        this.type = type;
        this.payload = payload;
        this.timestamp = timestamp;
    }

    /**
     * 테스트용 팩토리 메서드.
     *
     * @param sessionId 세션 ID
     * @param type      이벤트 타입
     * @param payload   이벤트 상세 데이터
     * @return 세션 이벤트 메시지
     */
    public static SessionEventMessage of(Long sessionId, SessionEventType type,
                                         Map<String, Object> payload) {
        return new SessionEventMessage(sessionId, type, payload, LocalDateTime.now());
    }

    public Long getSessionId() {
        return sessionId;
    }

    public SessionEventType getType() {
        return type;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
