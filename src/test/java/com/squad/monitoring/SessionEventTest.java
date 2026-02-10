package com.squad.monitoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionEventTest {

    @Test
    @DisplayName("of()로 생성 시 모든 필드가 올바르게 설정된다")
    void ofSetsAllFields() {
        Map<String, Object> payload = Map.of("key", "value");

        SessionEvent event = SessionEvent.of(1L, SessionEventType.MESSAGE, payload);

        assertThat(event.getSessionId()).isEqualTo(1L);
        assertThat(event.getType()).isEqualTo(SessionEventType.MESSAGE);
        assertThat(event.getPayload()).containsEntry("key", "value");
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("각 SessionEventType으로 이벤트를 생성할 수 있다")
    void ofSupportsAllEventTypes() {
        Map<String, Object> payload = Map.of();

        for (SessionEventType type : SessionEventType.values()) {
            SessionEvent event = SessionEvent.of(1L, type, payload);
            assertThat(event.getType()).isEqualTo(type);
        }
    }
}
