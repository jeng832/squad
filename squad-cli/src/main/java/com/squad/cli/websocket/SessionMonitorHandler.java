package com.squad.cli.websocket;

import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;

/**
 * 세션 모니터링용 STOMP 세션 핸들러.
 *
 * <p>STOMP 연결 성공 시 {@code /topic/sessions/{sessionId}}를 구독하고,
 * 수신된 이벤트를 터미널에 포맷팅하여 출력한다.</p>
 *
 * <p>{@link SessionEventType#SESSION_COMPLETE} 이벤트 수신 또는 오류 발생 시
 * {@link CountDownLatch}를 해제하여 호출부에서 종료를 감지할 수 있게 한다.</p>
 *
 * @see SessionEventMessage
 */
public class SessionMonitorHandler extends StompSessionHandlerAdapter {

    private static final String TOPIC_PREFIX = "/topic/sessions/";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String sessionId;
    private final PrintWriter writer;
    private final CountDownLatch completionLatch;

    /**
     * 세션 모니터 핸들러를 생성한다.
     *
     * @param sessionId       모니터링할 세션 ID
     * @param writer          터미널 출력용 PrintWriter
     * @param completionLatch 세션 완료 시 해제할 latch
     */
    public SessionMonitorHandler(String sessionId, PrintWriter writer,
                                 CountDownLatch completionLatch) {
        this.sessionId = sessionId;
        this.writer = writer;
        this.completionLatch = completionLatch;
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        writer.println("[모니터] 세션 " + sessionId + " 모니터링 시작...");
        writer.println("[모니터] Ctrl+C로 모니터링을 중단할 수 있습니다.");
        writer.println();
        writer.flush();

        session.subscribe(TOPIC_PREFIX + sessionId, this);
    }

    @Override
    public Type getPayloadType(StompHeaders headers) {
        return SessionEventMessage.class;
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
        if (!(payload instanceof SessionEventMessage event)) {
            return;
        }
        renderEvent(event);

        if (event.getType() == SessionEventType.SESSION_COMPLETE) {
            completionLatch.countDown();
        }
    }

    @Override
    public void handleException(StompSession session, StompCommand command,
                                StompHeaders headers, byte[] payload, Throwable exception) {
        writer.println("[오류] WebSocket 처리 오류: " + exception.getMessage());
        writer.flush();
        completionLatch.countDown();
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
        writer.println("[오류] WebSocket 연결 오류: " + exception.getMessage());
        writer.flush();
        completionLatch.countDown();
    }

    /**
     * 이벤트를 터미널에 출력한다.
     *
     * <p>이벤트 타입에 따라 다른 포맷으로 출력한다.</p>
     *
     * @param event 수신된 세션 이벤트
     */
    void renderEvent(SessionEventMessage event) {
        if (event.getType() == null || event.getPayload() == null) {
            return;
        }

        String timestamp = event.getTimestamp() != null
                ? event.getTimestamp().format(TIME_FORMATTER) : "??:??:??";

        switch (event.getType()) {
            case AGENT_STATUS -> renderAgentStatus(timestamp, event);
            case MESSAGE -> renderMessage(timestamp, event);
            case SESSION_COMPLETE -> renderSessionComplete(timestamp, event);
        }
        writer.flush();
    }

    private void renderAgentStatus(String timestamp, SessionEventMessage event) {
        String agentName = extractPayload(event, "agentName", "?");
        String status = extractPayload(event, "status", "?");
        writer.printf("[%s] [에이전트] %s → %s%n", timestamp, agentName, status);
    }

    private void renderMessage(String timestamp, SessionEventMessage event) {
        String from = extractPayload(event, "fromAgentId", "system");
        String to = extractPayload(event, "toAgentId", "broadcast");
        String messageType = extractPayload(event, "messageType", "");
        String content = extractPayload(event, "content", "");

        if (!messageType.isEmpty()) {
            writer.printf("[%s] [메시지] %s → %s (%s): %s%n", timestamp, from, to, messageType, content);
        } else {
            writer.printf("[%s] [메시지] %s → %s: %s%n", timestamp, from, to, content);
        }
    }

    private void renderSessionComplete(String timestamp, SessionEventMessage event) {
        String result = extractPayload(event, "result", "");
        writer.printf("[%s] [완료] 세션이 완료되었습니다.%n", timestamp);
        if (!result.isBlank()) {
            writer.println("  결과: " + result);
        }
    }

    private String extractPayload(SessionEventMessage event, String key, String defaultValue) {
        Object value = event.getPayload().get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }
}
