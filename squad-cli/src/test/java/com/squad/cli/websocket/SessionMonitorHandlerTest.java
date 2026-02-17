package com.squad.cli.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SessionMonitorHandlerTest {

    private StringWriter outputBuffer;
    private PrintWriter writer;
    private CountDownLatch latch;
    private SessionMonitorHandler handler;

    @BeforeEach
    void setUp() {
        outputBuffer = new StringWriter();
        writer = new PrintWriter(outputBuffer);
        latch = new CountDownLatch(1);
        handler = new SessionMonitorHandler("1", writer, latch);
    }

    @Test
    @DisplayName("연결 성공 시 모니터링 시작 메시지를 출력하고 토픽을 구독한다")
    void afterConnectedSubscribesToTopic() {
        StompSession session = mock(StompSession.class);
        StompHeaders headers = new StompHeaders();

        handler.afterConnected(session, headers);

        String output = outputBuffer.toString();
        assertThat(output).contains("세션 1 모니터링 시작");
        assertThat(output).contains("Ctrl+C");
        verify(session).subscribe(eq("/topic/sessions/1"), eq(handler));
    }

    @Test
    @DisplayName("payload 타입이 SessionEventMessage이다")
    void payloadTypeIsSessionEventMessage() {
        assertThat(handler.getPayloadType(new StompHeaders()))
                .isEqualTo(SessionEventMessage.class);
    }

    @Nested
    @DisplayName("이벤트 렌더링")
    class EventRenderingTests {

        @Test
        @DisplayName("AGENT_STATUS 이벤트를 출력한다")
        void renderAgentStatusEvent() {
            SessionEventMessage event = SessionEventMessage.of(1L, SessionEventType.AGENT_STATUS,
                    Map.of("agentName", "번역 워커", "status", "WORKING"));

            handler.renderEvent(event);

            String output = outputBuffer.toString();
            assertThat(output).contains("[에이전트]");
            assertThat(output).contains("번역 워커");
            assertThat(output).contains("WORKING");
        }

        @Test
        @DisplayName("MESSAGE 이벤트를 출력한다")
        void renderMessageEvent() {
            SessionEventMessage event = SessionEventMessage.of(1L, SessionEventType.MESSAGE,
                    Map.of("fromAgentId", "1", "toAgentId", "2",
                            "messageType", "TASK_REQUEST", "content", "번역 작업"));

            handler.renderEvent(event);

            String output = outputBuffer.toString();
            assertThat(output).contains("[메시지]");
            assertThat(output).contains("1");
            assertThat(output).contains("2");
            assertThat(output).contains("TASK_REQUEST");
            assertThat(output).contains("번역 작업");
        }

        @Test
        @DisplayName("MESSAGE 이벤트에 messageType이 없으면 타입 없이 출력한다")
        void renderMessageEventWithoutType() {
            SessionEventMessage event = SessionEventMessage.of(1L, SessionEventType.MESSAGE,
                    Map.of("fromAgentId", "system", "toAgentId", "broadcast",
                            "content", "알림 메시지"));

            handler.renderEvent(event);

            String output = outputBuffer.toString();
            assertThat(output).contains("[메시지]");
            assertThat(output).contains("system");
            assertThat(output).contains("알림 메시지");
        }

        @Test
        @DisplayName("SESSION_COMPLETE 이벤트를 출력하고 latch를 해제한다")
        void renderSessionCompleteEvent() {
            SessionEventMessage event = SessionEventMessage.of(1L, SessionEventType.SESSION_COMPLETE,
                    Map.of("result", "작업이 성공적으로 완료되었습니다."));

            handler.handleFrame(new StompHeaders(), event);

            String output = outputBuffer.toString();
            assertThat(output).contains("[완료]");
            assertThat(output).contains("세션이 완료되었습니다");
            assertThat(output).contains("작업이 성공적으로 완료되었습니다");
            assertThat(latch.getCount()).isZero();
        }

        @Test
        @DisplayName("SESSION_COMPLETE 결과가 빈 문자열이면 결과를 출력하지 않는다")
        void renderSessionCompleteWithoutResult() {
            SessionEventMessage event = SessionEventMessage.of(1L, SessionEventType.SESSION_COMPLETE,
                    Map.of("result", ""));

            handler.renderEvent(event);

            String output = outputBuffer.toString();
            assertThat(output).contains("[완료]");
            assertThat(output).doesNotContain("결과:");
        }

        @Test
        @DisplayName("type이 null인 이벤트는 무시한다")
        void ignoreEventWithNullType() {
            SessionEventMessage event = SessionEventMessage.of(1L, null, Map.of());

            handler.renderEvent(event);

            assertThat(outputBuffer.toString()).isEmpty();
        }

        @Test
        @DisplayName("payload가 null인 이벤트는 무시한다")
        void ignoreEventWithNullPayload() {
            SessionEventMessage event = SessionEventMessage.of(1L, SessionEventType.MESSAGE, null);

            handler.renderEvent(event);

            assertThat(outputBuffer.toString()).isEmpty();
        }
    }

    @Nested
    @DisplayName("오류 처리")
    class ErrorHandlingTests {

        @Test
        @DisplayName("전송 오류 시 오류 메시지를 출력하고 latch를 해제한다")
        void handleTransportError() {
            StompSession session = mock(StompSession.class);

            handler.handleTransportError(session, new RuntimeException("연결 끊김"));

            assertThat(outputBuffer.toString()).contains("[오류]").contains("연결 끊김");
            assertThat(latch.getCount()).isZero();
        }

        @Test
        @DisplayName("예외 발생 시 오류 메시지를 출력하고 latch를 해제한다")
        void handleException() {
            StompSession session = mock(StompSession.class);

            handler.handleException(session, null, new StompHeaders(), null,
                    new RuntimeException("처리 오류"));

            assertThat(outputBuffer.toString()).contains("[오류]").contains("처리 오류");
            assertThat(latch.getCount()).isZero();
        }
    }

    @Nested
    @DisplayName("STOMP 이벤트가 아닌 payload")
    class NonEventPayloadTests {

        @Test
        @DisplayName("SessionEventMessage가 아닌 payload는 무시한다")
        void ignoreNonEventPayload() {
            handler.handleFrame(new StompHeaders(), "not an event");

            assertThat(outputBuffer.toString()).isEmpty();
            assertThat(latch.getCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("AGENT_STATUS 이벤트는 latch를 해제하지 않는다")
        void agentStatusDoesNotReleaseLatch() {
            SessionEventMessage event = SessionEventMessage.of(1L, SessionEventType.AGENT_STATUS,
                    Map.of("agentName", "워커", "status", "IDLE"));

            handler.handleFrame(new StompHeaders(), event);

            assertThat(latch.getCount()).isEqualTo(1);
        }
    }
}
