package com.squad.agent.service;

import com.squad.agent.domain.Agent;
import com.squad.agent.dto.AgentCreateRequest;
import com.squad.agent.dto.AgentResponse;
import com.squad.agent.dto.AgentUpdateRequest;
import com.squad.agent.repository.AgentRepository;
import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import com.squad.common.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

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
        validateLlmConfig(request.llmConfig());
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
        validateLlmConfig(request.llmConfig());
        agent.update(request.name(), request.role(), request.llmConfig());
        return AgentResponse.from(agent);
    }

    /**
     * llmConfig에 apiKey가 설정되어 있는지 검증한다.
     *
     * @param llmConfig Agent의 LLM 설정 맵
     * @throws ValidationException apiKey가 없거나 비어 있는 경우
     */
    private void validateLlmConfig(Map<String, Object> llmConfig) {
        Object apiKey = llmConfig.get("apiKey");
        if (apiKey == null || apiKey.toString().isBlank()) {
            throw new ValidationException(ErrorCode.INVALID_REQUEST,
                    "llmConfig에 apiKey는 필수입니다. 직접 키 또는 ref:secret/<name> 형식으로 입력하세요.");
        }
    }

    @Transactional
    public void delete(Long id) {
        agentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AGENT_NOT_FOUND));
        agentRepository.deleteById(id);
    }
}
