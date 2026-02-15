package com.squad.session.service;

import com.squad.common.exception.NotFoundException;
import com.squad.session.domain.Message;
import com.squad.session.domain.MessageType;
import com.squad.session.domain.Session;
import com.squad.session.domain.SessionStatus;
import com.squad.session.dto.MessageResponse;
import com.squad.session.repository.MessageRepository;
import com.squad.session.repository.SessionRepository;
import com.squad.squad.domain.Squad;
import com.squad.agent.domain.Agent;
import com.squad.agent.domain.RoleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageService 단위 테스트")
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private SessionRepository sessionRepository;

    @InjectMocks
    private MessageService messageService;

    private Session createSession() {
        Agent orchestrator = Agent.builder()
                .id(1L).name("orch").roleType(RoleType.ORCHESTRATOR)
                .role("역할").llmConfig(Map.of("provider", "claude"))
                .build();
        Squad squad = Squad.builder()
                .id(1L).name("squad").orchestrator(orchestrator).build();
        return Session.builder()
                .id(1L).squad(squad).userPrompt("프롬프트").status(SessionStatus.RUNNING).build();
    }

    private Message createMessage(Long id, MessageType type) {
        return Message.builder()
                .id(id)
                .session(createSession())
                .fromAgentId(1L)
                .toAgentId(2L)
                .content("메시지 내용")
                .type(type)
                .build();
    }

    @Test
    @DisplayName("세션의 전체 메시지를 조회한다 (type=null)")
    void findBySessionAllTypes() {
        given(sessionRepository.existsById(1L)).willReturn(true);
        given(messageRepository.findBySessionIdOrderByCreatedAt(1L))
                .willReturn(List.of(
                        createMessage(1L, MessageType.TASK_REQUEST),
                        createMessage(2L, MessageType.TASK_RESULT)
                ));

        List<MessageResponse> result = messageService.findBySession(1L, null);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("세션의 메시지를 type으로 필터링하여 조회한다")
    void findBySessionWithType() {
        given(sessionRepository.existsById(1L)).willReturn(true);
        given(messageRepository.findBySessionIdAndTypeOrderByCreatedAt(1L, MessageType.TASK_REQUEST))
                .willReturn(List.of(createMessage(1L, MessageType.TASK_REQUEST)));

        List<MessageResponse> result = messageService.findBySession(1L, MessageType.TASK_REQUEST);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo(MessageType.TASK_REQUEST);
    }

    @Test
    @DisplayName("존재하지 않는 세션의 메시지 조회 시 NotFoundException 발생")
    void findBySessionNotFound() {
        given(sessionRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> messageService.findBySession(99L, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("존재하지 않는 세션에 type 필터 조회 시에도 NotFoundException 발생")
    void findBySessionNotFoundWithType() {
        given(sessionRepository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> messageService.findBySession(99L, MessageType.TASK_REQUEST))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("메시지가 없는 세션 조회 시 빈 리스트를 반환한다")
    void findBySessionEmpty() {
        given(sessionRepository.existsById(1L)).willReturn(true);
        given(messageRepository.findBySessionIdOrderByCreatedAt(1L)).willReturn(List.of());

        List<MessageResponse> result = messageService.findBySession(1L, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("SYSTEM 타입 메시지를 필터링하여 조회한다")
    void findBySessionSystemType() {
        given(sessionRepository.existsById(1L)).willReturn(true);
        given(messageRepository.findBySessionIdAndTypeOrderByCreatedAt(1L, MessageType.SYSTEM))
                .willReturn(List.of(createMessage(3L, MessageType.SYSTEM)));

        List<MessageResponse> result = messageService.findBySession(1L, MessageType.SYSTEM);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo(MessageType.SYSTEM);
    }

    @Test
    @DisplayName("메시지 응답에 fromAgentId와 toAgentId가 포함된다")
    void messageResponseContainsAgentIds() {
        given(sessionRepository.existsById(1L)).willReturn(true);
        given(messageRepository.findBySessionIdOrderByCreatedAt(1L))
                .willReturn(List.of(createMessage(1L, MessageType.TASK_REQUEST)));

        List<MessageResponse> result = messageService.findBySession(1L, null);

        assertThat(result.get(0).fromAgentId()).isEqualTo(1L);
        assertThat(result.get(0).toAgentId()).isEqualTo(2L);
        assertThat(result.get(0).content()).isEqualTo("메시지 내용");
    }
}
