package com.squad.session.dto;

import com.squad.session.domain.Session;
import com.squad.session.domain.SessionStatus;

import java.time.LocalDateTime;

/**
 * 세션 응답 DTO.
 */
public record SessionResponse(
        Long id,
        Long squadId,
        String userPrompt,
        SessionStatus status,
        String result,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {

    public static SessionResponse from(Session session) {
        return new SessionResponse(
                session.getId(),
                session.getSquad().getId(),
                session.getUserPrompt(),
                session.getStatus(),
                session.getResult(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getCreatedAt()
        );
    }
}
