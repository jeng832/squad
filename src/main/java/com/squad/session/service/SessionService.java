package com.squad.session.service;

import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import com.squad.common.exception.ValidationException;
import com.squad.session.domain.Session;
import com.squad.session.domain.SessionStatus;
import com.squad.session.dto.SessionCreateRequest;
import com.squad.session.dto.SessionResponse;
import com.squad.session.repository.SessionRepository;
import com.squad.squad.domain.Squad;
import com.squad.squad.repository.SquadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SessionService {

    private final SessionRepository sessionRepository;
    private final SquadRepository squadRepository;

    public SessionService(SessionRepository sessionRepository, SquadRepository squadRepository) {
        this.sessionRepository = sessionRepository;
        this.squadRepository = squadRepository;
    }

    public List<SessionResponse> findAll() {
        return sessionRepository.findAll().stream()
                .map(SessionResponse::from)
                .toList();
    }

    public SessionResponse findById(Long id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SESSION_NOT_FOUND));
        return SessionResponse.from(session);
    }

    @Transactional
    public SessionResponse create(SessionCreateRequest request) {
        Squad squad = squadRepository.findById(request.squadId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.SQUAD_NOT_FOUND));

        Session session = Session.builder()
                .squad(squad)
                .userPrompt(request.userPrompt())
                .status(SessionStatus.PENDING)
                .build();

        return SessionResponse.from(sessionRepository.save(session));
    }

    @Transactional
    public SessionResponse cancel(Long id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.CANCELLED) {
            throw new ValidationException(ErrorCode.INVALID_SESSION_STATE, "완료된 세션은 취소할 수 없습니다.");
        }

        session.cancel();
        return SessionResponse.from(session);
    }
}
