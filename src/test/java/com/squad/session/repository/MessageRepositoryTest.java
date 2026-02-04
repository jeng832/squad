package com.squad.session.repository;

import com.squad.agent.domain.Agent;
import com.squad.agent.domain.RoleType;
import com.squad.session.domain.Message;
import com.squad.session.domain.MessageType;
import com.squad.session.domain.Session;
import com.squad.squad.domain.Squad;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MessageRepositoryTest {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Session persistSession() {
        Agent agent = entityManager.persist(Agent.builder()
                .name("orch")
                .roleType(RoleType.ORCHESTRATOR)
                .role("orchestrator role")
                .llmConfig(Map.of("model", "claude-sonnet-4-20250514"))
                .build());
        entityManager.flush();

        Squad squad = entityManager.persist(Squad.builder()
                .name("alpha")
                .orchestrator(agent)
                .build());
        entityManager.flush();

        Session session = entityManager.persist(Session.builder()
                .squad(squad)
                .userPrompt("테스트 프롬프트")
                .build());
        entityManager.flush();
        return session;
    }

    private Message buildMessage(Session session, String content, MessageType type, Long fromId, Long toId) {
        return Message.builder()
                .session(session)
                .content(content)
                .type(type)
                .fromAgentId(fromId)
                .toAgentId(toId)
                .build();
    }

    @Test
    void Message_저장_후_ID로_조회_시_저장된_Message_반환() {
        Session session = persistSession();

        Message message = buildMessage(session, "작업 시작", MessageType.TASK_REQUEST, 1L, 2L);
        entityManager.persist(message);
        entityManager.flush();
        entityManager.clear();

        Message found = messageRepository.findById(message.getId()).orElseThrow();

        assertThat(found.getContent()).isEqualTo("작업 시작");
        assertThat(found.getType()).isEqualTo(MessageType.TASK_REQUEST);
        assertThat(found.getFromAgentId()).isEqualTo(1L);
        assertThat(found.getToAgentId()).isEqualTo(2L);
        assertThat(found.getSession().getId()).isEqualTo(session.getId());
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void Message_SessionId로_조회_시_시간순_Message_목록_반환() {
        Session session = persistSession();

        entityManager.persist(buildMessage(session, "첫 번째 메시지", MessageType.TASK_REQUEST, 1L, 2L));
        entityManager.flush();

        // createdAt 간격 보장을 위해 별도 persist
        entityManager.persist(buildMessage(session, "두 번째 메시지", MessageType.TASK_RESULT, 2L, 1L));
        entityManager.flush();

        entityManager.persist(buildMessage(session, "세 번째 메시지", MessageType.SYSTEM, null, null));
        entityManager.flush();
        entityManager.clear();

        List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAt(session.getId());

        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).getContent()).isEqualTo("첫 번째 메시지");
        assertThat(messages.get(1).getContent()).isEqualTo("두 번째 메시지");
        assertThat(messages.get(2).getContent()).isEqualTo("세 번째 메시지");
    }

    @Test
    void Message_시스템_메시지_저장_시_from_to_null_가능() {
        Session session = persistSession();

        Message sysMessage = buildMessage(session, "세션 시작 알림", MessageType.SYSTEM, null, null);
        entityManager.persist(sysMessage);
        entityManager.flush();
        entityManager.clear();

        Message found = messageRepository.findById(sysMessage.getId()).orElseThrow();

        assertThat(found.getType()).isEqualTo(MessageType.SYSTEM);
        assertThat(found.getFromAgentId()).isNull();
        assertThat(found.getToAgentId()).isNull();
        assertThat(found.getContent()).isEqualTo("세션 시작 알림");
    }

    @Test
    void Message_SessionId_및_Type으로_조회_시_해당_Message_목록_반환() {
        Session session = persistSession();

        entityManager.persist(buildMessage(session, "작업 요청 1", MessageType.TASK_REQUEST, 1L, 2L));
        entityManager.persist(buildMessage(session, "작업 결과 1", MessageType.TASK_RESULT, 2L, 1L));
        entityManager.persist(buildMessage(session, "작업 요청 2", MessageType.TASK_REQUEST, 1L, 3L));
        entityManager.persist(buildMessage(session, "도움 요청", MessageType.HELP_REQUEST, 2L, 3L));
        entityManager.flush();
        entityManager.clear();

        List<Message> taskRequests = messageRepository.findBySessionIdAndType(session.getId(), MessageType.TASK_REQUEST);

        assertThat(taskRequests).hasSize(2);
        assertThat(taskRequests.stream().map(Message::getContent))
                .containsExactlyInAnyOrder("작업 요청 1", "작업 요청 2");
    }
}
