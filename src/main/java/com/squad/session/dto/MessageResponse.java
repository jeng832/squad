package com.squad.session.dto;

import com.squad.session.domain.Message;
import com.squad.session.domain.MessageType;

import java.time.LocalDateTime;

/**
 * 세션 메시지 응답 DTO.
 */
public record MessageResponse(
        Long id,
        Long sessionId,
        Long fromAgentId,
        Long toAgentId,
        String content,
        MessageType type,
        LocalDateTime createdAt
) {

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getSession().getId(),
                message.getFromAgentId(),
                message.getToAgentId(),
                message.getContent(),
                message.getType(),
                message.getCreatedAt()
        );
    }
}
