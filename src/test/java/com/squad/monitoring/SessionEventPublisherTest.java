package com.squad.monitoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SessionEventPublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private SessionEventPublisher sessionEventPublisher;

    @Test
    @DisplayName("publish()는 /topic/sessions/{sessionId} 경로로 이벤트를 전송한다")
    void publishSendsToCorrectDestination() {
        SessionEvent event = SessionEvent.of(42L, SessionEventType.MESSAGE, java.util.Map.of());

        sessionEventPublisher.publish(event);

        verify(messagingTemplate).convertAndSend(eq("/topic/sessions/42"), eq(event));
    }

    @Test
    @DisplayName("publishMessage()는 MESSAGE 타입 이벤트를 생성하여 전송한다")
    void publishMessageCreatesMessageEvent() {
        sessionEventPublisher.publishMessage(1L, 10L, 20L, "TASK_REQUEST", "작업 내용");

        ArgumentCaptor<SessionEvent> captor = ArgumentCaptor.forClass(SessionEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/sessions/1"), captor.capture());

        SessionEvent event = captor.getValue();
        assertThat(event.getType()).isEqualTo(SessionEventType.MESSAGE);
        assertThat(event.getSessionId()).isEqualTo(1L);
        assertThat(event.getPayload()).containsEntry("fromAgentId", 10L);
        assertThat(event.getPayload()).containsEntry("toAgentId", 20L);
        assertThat(event.getPayload()).containsEntry("messageType", "TASK_REQUEST");
        assertThat(event.getPayload()).containsEntry("content", "작업 내용");
    }

    @Test
    @DisplayName("publishMessage()는 null agentId를 적절한 문자열로 변환한다")
    void publishMessageHandlesNullAgentIds() {
        sessionEventPublisher.publishMessage(1L, null, null, "SYSTEM", "시스템 메시지");

        ArgumentCaptor<SessionEvent> captor = ArgumentCaptor.forClass(SessionEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/sessions/1"), captor.capture());

        SessionEvent event = captor.getValue();
        assertThat(event.getPayload()).containsEntry("fromAgentId", "system");
        assertThat(event.getPayload()).containsEntry("toAgentId", "broadcast");
    }

    @Test
    @DisplayName("publishAgentStatus()는 AGENT_STATUS 타입 이벤트를 생성하여 전송한다")
    void publishAgentStatusCreatesAgentStatusEvent() {
        sessionEventPublisher.publishAgentStatus(1L, 10L, "코드분석기", "WORKING");

        ArgumentCaptor<SessionEvent> captor = ArgumentCaptor.forClass(SessionEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/sessions/1"), captor.capture());

        SessionEvent event = captor.getValue();
        assertThat(event.getType()).isEqualTo(SessionEventType.AGENT_STATUS);
        assertThat(event.getPayload()).containsEntry("agentId", 10L);
        assertThat(event.getPayload()).containsEntry("agentName", "코드분석기");
        assertThat(event.getPayload()).containsEntry("status", "WORKING");
    }

    @Test
    @DisplayName("publishMessage()는 200자 이상의 content를 잘라낸다")
    void publishMessageTruncatesLongContent() {
        String longContent = "x".repeat(300);

        sessionEventPublisher.publishMessage(1L, 10L, 20L, "TASK_RESULT", longContent);

        ArgumentCaptor<SessionEvent> captor = ArgumentCaptor.forClass(SessionEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/sessions/1"), captor.capture());

        String content = (String) captor.getValue().getPayload().get("content");
        assertThat(content).hasSize(203); // 200 + "..."
        assertThat(content).endsWith("...");
    }

    @Test
    @DisplayName("publishSessionComplete()는 SESSION_COMPLETE 타입 이벤트를 생성하여 전송한다")
    void publishSessionCompleteCreatesSessionCompleteEvent() {
        sessionEventPublisher.publishSessionComplete(1L, "최종 결과입니다");

        ArgumentCaptor<SessionEvent> captor = ArgumentCaptor.forClass(SessionEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/sessions/1"), captor.capture());

        SessionEvent event = captor.getValue();
        assertThat(event.getType()).isEqualTo(SessionEventType.SESSION_COMPLETE);
        assertThat(event.getSessionId()).isEqualTo(1L);
        assertThat(event.getPayload()).containsEntry("result", "최종 결과입니다");
    }

    @Test
    @DisplayName("publishSessionComplete()는 500자 이상의 result를 잘라낸다")
    void publishSessionCompleteTruncatesLongResult() {
        String longResult = "y".repeat(600);

        sessionEventPublisher.publishSessionComplete(1L, longResult);

        ArgumentCaptor<SessionEvent> captor = ArgumentCaptor.forClass(SessionEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/sessions/1"), captor.capture());

        String result = (String) captor.getValue().getPayload().get("result");
        assertThat(result).hasSize(503); // 500 + "..."
        assertThat(result).endsWith("...");
    }
}
