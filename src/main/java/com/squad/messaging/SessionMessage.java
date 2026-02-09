package com.squad.messaging;

import com.squad.session.domain.MessageType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 세션 내 에이전트 간 통신에 사용되는 메시지 DTO.
 *
 * <p>메시지의 라우팅 정보(sessionId, fromAgentId, toAgentId)와
 * 메시지 내용(type, content)을 포함하며, {@link MessagePublisher}를 통해 발행된다.</p>
 *
 * @see MessageType
 * @see MessagePublisher
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SessionMessage {

    private Long sessionId;
    private Long fromAgentId;
    private Long toAgentId;
    private MessageType type;
    private String content;
    private LocalDateTime timestamp;

    /**
     * 세션 메시지를 생성한다.
     *
     * @param sessionId   세션 ID
     * @param fromAgentId 발신 Agent ID (시스템 메시지인 경우 null)
     * @param toAgentId   수신 Agent ID (브로드캐스트인 경우 null)
     * @param type        메시지 타입
     * @param content     메시지 내용
     * @return 세션 메시지
     */
    public static SessionMessage of(Long sessionId, Long fromAgentId, Long toAgentId,
                                     MessageType type, String content) {
        return new SessionMessage(sessionId, fromAgentId, toAgentId, type, content, LocalDateTime.now());
    }

    /**
     * 시스템 메시지를 생성한다.
     *
     * @param sessionId 세션 ID
     * @param content   메시지 내용
     * @return SYSTEM 타입의 세션 메시지
     */
    public static SessionMessage system(Long sessionId, String content) {
        return of(sessionId, null, null, MessageType.SYSTEM, content);
    }
}