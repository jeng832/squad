package com.squad.squad.repository;

import com.squad.squad.domain.Squad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SquadRepository extends JpaRepository<Squad, Long> {

    List<Squad> findByOrchestraterId(Long orchestratorId);
}
