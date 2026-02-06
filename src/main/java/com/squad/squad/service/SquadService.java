package com.squad.squad.service;

import com.squad.agent.domain.Agent;
import com.squad.agent.domain.RoleType;
import com.squad.agent.repository.AgentRepository;
import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import com.squad.common.exception.ValidationException;
import com.squad.squad.domain.Squad;
import com.squad.squad.dto.SquadCreateRequest;
import com.squad.squad.dto.SquadResponse;
import com.squad.squad.dto.SquadUpdateRequest;
import com.squad.squad.repository.SquadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SquadService {

    private final SquadRepository squadRepository;
    private final AgentRepository agentRepository;

    public SquadService(SquadRepository squadRepository, AgentRepository agentRepository) {
        this.squadRepository = squadRepository;
        this.agentRepository = agentRepository;
    }

    public List<SquadResponse> findAll() {
        return squadRepository.findAll().stream()
                .map(SquadResponse::from)
                .toList();
    }

    public SquadResponse findById(Long id) {
        Squad squad = squadRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SQUAD_NOT_FOUND));
        return SquadResponse.from(squad);
    }

    @Transactional
    public SquadResponse create(SquadCreateRequest request) {
        Agent orchestrator = validateOrchestrator(request.orchestratorId());

        Squad squad = Squad.builder()
                .name(request.name())
                .description(request.description())
                .orchestrator(orchestrator)
                .directCommunication(request.directCommunication())
                .build();
        Squad saved = squadRepository.save(squad);

        if (request.agentIds() != null) {
            for (Long agentId : request.agentIds()) {
                Agent agent = agentRepository.findById(agentId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.AGENT_NOT_FOUND));
                saved.addAgent(agent);
            }
        }

        return SquadResponse.from(saved);
    }

    @Transactional
    public SquadResponse update(Long id, SquadUpdateRequest request) {
        Squad squad = squadRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SQUAD_NOT_FOUND));

        squad.update(request.name(), request.description(), request.directCommunication());

        if (request.agentIds() != null) {
            squad.getAgents().clear();
            for (Long agentId : request.agentIds()) {
                Agent agent = agentRepository.findById(agentId)
                        .orElseThrow(() -> new NotFoundException(ErrorCode.AGENT_NOT_FOUND));
                squad.addAgent(agent);
            }
        }

        return SquadResponse.from(squad);
    }

    @Transactional
    public void delete(Long id) {
        squadRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SQUAD_NOT_FOUND));
        squadRepository.deleteById(id);
    }

    /**
     * Orchestrator Agent의 존재 여부와 roleType을 검증합니다.
     */
    private Agent validateOrchestrator(Long orchestratorId) {
        Agent orchestrator = agentRepository.findById(orchestratorId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AGENT_NOT_FOUND));
        if (orchestrator.getRoleType() != RoleType.ORCHESTRATOR) {
            throw new ValidationException(ErrorCode.INVALID_ORCHESTRATOR_ROLE);
        }
        return orchestrator;
    }
}
