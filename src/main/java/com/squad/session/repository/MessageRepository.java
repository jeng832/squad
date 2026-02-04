package com.squad.session.repository;

import com.squad.session.domain.Message;
import com.squad.session.domain.MessageType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findBySessionIdOrderByCreatedAt(Long sessionId);

    List<Message> findBySessionIdAndType(Long sessionId, MessageType type);
}
