package com.squad.session.service;

import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import com.squad.session.domain.MessageType;
import com.squad.session.dto.MessageResponse;
import com.squad.session.repository.MessageRepository;
import com.squad.session.repository.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final SessionRepository sessionRepository;

    public MessageService(MessageRepository messageRepository, SessionRepository sessionRepository) {
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
    }

    public List<MessageResponse> findBySession(Long sessionId, MessageType type) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new NotFoundException(ErrorCode.SESSION_NOT_FOUND);
        }

        if (type == null) {
            return messageRepository.findBySessionIdOrderByCreatedAt(sessionId).stream()
                    .map(MessageResponse::from)
                    .toList();
        }

        return messageRepository.findBySessionIdAndTypeOrderByCreatedAt(sessionId, type).stream()
                .map(MessageResponse::from)
                .toList();
    }
}
