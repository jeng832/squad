package com.squad.agent.repository;

import com.squad.agent.domain.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Agent JPA Repository.
 */
public interface AgentRepository extends JpaRepository<Agent, Long> {
}
