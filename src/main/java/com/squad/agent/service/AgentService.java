package com.squad.agent.service;

import com.squad.agent.domain.Agent;
import com.squad.agent.dto.AgentCreateRequest;
import com.squad.agent.dto.AgentResponse;
import com.squad.agent.dto.AgentUpdateRequest;
import com.squad.agent.repository.AgentRepository;
import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AgentService {

    private final AgentRepository agentRepository;

    public AgentService(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public List<AgentResponse> findAll() {
        return agentRepository.findAll().stream()
                .map(AgentResponse::from)
                .toList();
    }

    public AgentResponse findById(Long id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AGENT_NOT_FOUND));
        return AgentResponse.from(agent);
    }

    @Transactional
    public AgentResponse create(AgentCreateRequest request) {
        Agent agent = Agent.builder()
                .name(request.name())
                .roleType(request.roleType())
                .role(request.role())
                .llmConfig(request.llmConfig())
                .build();
        return AgentResponse.from(agentRepository.save(agent));
    }

    @Transactional
    public AgentResponse update(Long id, AgentUpdateRequest request) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AGENT_NOT_FOUND));
        agent.update(request.name(), request.role(), request.llmConfig());
        return AgentResponse.from(agent);
    }

    @Transactional
    public void delete(Long id) {
        agentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AGENT_NOT_FOUND));
        agentRepository.deleteById(id);
    }
}
