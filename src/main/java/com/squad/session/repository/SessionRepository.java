package com.squad.session.repository;

import com.squad.session.domain.Session;
import com.squad.session.domain.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionRepository extends JpaRepository<Session, Long> {

    List<Session> findBySquadId(Long squadId);

    List<Session> findByStatus(SessionStatus status);
}
