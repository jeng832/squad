package com.squad.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 세션 이벤트를 WebSocket 클라이언트에게 발행하는 서비스.
 *
 * <p>{@link SimpMessagingTemplate}을 사용하여 STOMP 채널
 * {@code /topic/sessions/{sessionId}}로 이벤트를 전송한다.</p>
 *
 * <p>Orchestrator/Worker 서비스에서 주요 상태 변화 시 이 서비스를 호출하여
 * 클라이언트에게 실시간으로 진행 상황을 알린다.</p>
 *
 * @see SessionEvent
 * @see SessionEventType
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionEventPublisher {

    private static final String TOPIC_PREFIX = "/topic/sessions/";

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 세션 이벤트를 WebSocket 클라이언트에게 전송한다.
     *
     * <p>이벤트 발행 실패는 핵심 비즈니스 로직에 영향을 주지 않도록
     * 예외를 내부에서 처리하고 로그만 남긴다.</p>
     *
     * @param event 전송할 세션 이벤트
     */
    public void publish(SessionEvent event) {
        try {
            String destination = TOPIC_PREFIX + event.getSessionId();
            messagingTemplate.convertAndSend(destination, event);

            log.debug("세션 이벤트 발행: sessionId={}, type={}, destination={}",
                    event.getSessionId(), event.getType(), destination);
        } catch (Exception e) {
            log.warn("세션 이벤트 발행 실패: sessionId={}, type={}",
                    event.getSessionId(), event.getType(), e);
        }
    }

    /**
     * MESSAGE 타입 이벤트를 발행한다.
     *
     * <p>Agent 간 메시지(TASK_REQUEST, TASK_RESULT 등) 발생 시 호출한다.</p>
     *
     * @param sessionId   세션 ID
     * @param fromAgentId 발신 Agent ID (nullable)
     * @param toAgentId   수신 Agent ID (nullable)
     * @param messageType 메시지 타입 (TASK_REQUEST, TASK_RESULT 등)
     * @param content     메시지 내용 (요약)
     */
    public void publishMessage(Long sessionId, Long fromAgentId, Long toAgentId,
                               String messageType, String content) {
        SessionEvent event = SessionEvent.of(sessionId, SessionEventType.MESSAGE, Map.of(
                "fromAgentId", fromAgentId != null ? fromAgentId : "system",
                "toAgentId", toAgentId != null ? toAgentId : "broadcast",
                "messageType", messageType,
                "content", truncate(content, 200)
        ));
        publish(event);
    }

    /**
     * AGENT_STATUS 타입 이벤트를 발행한다.
     *
     * <p>Agent의 상태가 변경될 때 호출한다.</p>
     *
     * @param sessionId 세션 ID
     * @param agentId   Agent ID
     * @param agentName Agent 이름
     * @param status    상태 (예: "WORKING", "IDLE", "DELEGATING")
     */
    public void publishAgentStatus(Long sessionId, Long agentId, String agentName,
                                   String status) {
        SessionEvent event = SessionEvent.of(sessionId, SessionEventType.AGENT_STATUS, Map.of(
                "agentId", agentId,
                "agentName", agentName,
                "status", status
        ));
        publish(event);
    }

    /**
     * SESSION_COMPLETE 타입 이벤트를 발행한다.
     *
     * <p>세션이 완료되었을 때 호출하여 클라이언트에게 최종 결과를 전달한다.
     * 클라이언트는 이 이벤트를 수신한 후 WebSocket 연결을 종료할 수 있다.</p>
     *
     * @param sessionId 세션 ID
     * @param result    세션 최종 결과 (요약)
     */
    public void publishSessionComplete(Long sessionId, String result) {
        SessionEvent event = SessionEvent.of(sessionId, SessionEventType.SESSION_COMPLETE, Map.of(
                "result", truncate(result, 500)
        ));
        publish(event);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
