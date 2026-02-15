package com.squad.agent.service;

import com.squad.agent.domain.Agent;
import com.squad.agent.domain.RoleType;
import com.squad.agent.dto.AgentCreateRequest;
import com.squad.agent.dto.AgentResponse;
import com.squad.agent.dto.AgentUpdateRequest;
import com.squad.agent.repository.AgentRepository;
import com.squad.common.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentService 단위 테스트")
class AgentServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @InjectMocks
    private AgentService agentService;

    private Agent createAgent(Long id, String name, RoleType roleType) {
        return Agent.builder()
                .id(id)
                .name(name)
                .roleType(roleType)
                .role("테스트 역할")
                .llmConfig(Map.of("provider", "claude"))
                .build();
    }

    @Test
    @DisplayName("전체 에이전트 목록을 조회한다")
    void findAll() {
        Agent agent1 = createAgent(1L, "agent-1", RoleType.ORCHESTRATOR);
        Agent agent2 = createAgent(2L, "agent-2", RoleType.WORKER);
        given(agentRepository.findAll()).willReturn(List.of(agent1, agent2));

        List<AgentResponse> result = agentService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("agent-1");
        assertThat(result.get(1).name()).isEqualTo("agent-2");
    }

    @Test
    @DisplayName("빈 목록 조회 시 빈 리스트를 반환한다")
    void findAllEmpty() {
        given(agentRepository.findAll()).willReturn(List.of());

        List<AgentResponse> result = agentService.findAll();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("ID로 에이전트를 조회한다")
    void findById() {
        Agent agent = createAgent(1L, "agent-1", RoleType.ORCHESTRATOR);
        given(agentRepository.findById(1L)).willReturn(Optional.of(agent));

        AgentResponse result = agentService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("agent-1");
        assertThat(result.roleType()).isEqualTo(RoleType.ORCHESTRATOR);
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 NotFoundException 발생")
    void findByIdNotFound() {
        given(agentRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> agentService.findById(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("에이전트를 생성한다")
    void create() {
        AgentCreateRequest request = new AgentCreateRequest(
                "new-agent", RoleType.WORKER, "역할 설명", Map.of("provider", "claude")
        );
        Agent saved = createAgent(1L, "new-agent", RoleType.WORKER);
        given(agentRepository.save(any(Agent.class))).willReturn(saved);

        AgentResponse result = agentService.create(request);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("new-agent");
        verify(agentRepository).save(any(Agent.class));
    }

    @Test
    @DisplayName("에이전트를 수정한다")
    void update() {
        Agent agent = createAgent(1L, "agent-1", RoleType.WORKER);
        given(agentRepository.findById(1L)).willReturn(Optional.of(agent));
        AgentUpdateRequest request = new AgentUpdateRequest(
                "updated-agent", "수정된 역할", Map.of("provider", "openai")
        );

        AgentResponse result = agentService.update(1L, request);

        assertThat(result.name()).isEqualTo("updated-agent");
    }

    @Test
    @DisplayName("존재하지 않는 에이전트 수정 시 NotFoundException 발생")
    void updateNotFound() {
        given(agentRepository.findById(99L)).willReturn(Optional.empty());
        AgentUpdateRequest request = new AgentUpdateRequest("name", "role", Map.of());

        assertThatThrownBy(() -> agentService.update(99L, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("에이전트를 삭제한다")
    void delete() {
        Agent agent = createAgent(1L, "agent-1", RoleType.WORKER);
        given(agentRepository.findById(1L)).willReturn(Optional.of(agent));

        agentService.delete(1L);

        verify(agentRepository).deleteById(1L);
    }

    @Test
    @DisplayName("존재하지 않는 에이전트 삭제 시 NotFoundException 발생")
    void deleteNotFound() {
        given(agentRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> agentService.delete(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("에이전트 수정 시 Agent 엔티티의 update 메서드가 호출된다")
    void updateCallsAgentUpdate() {
        Agent agent = createAgent(1L, "agent-1", RoleType.WORKER);
        given(agentRepository.findById(1L)).willReturn(Optional.of(agent));
        AgentUpdateRequest request = new AgentUpdateRequest(
                "updated", "새 역할", Map.of("model", "gpt-4")
        );

        agentService.update(1L, request);

        assertThat(agent.getName()).isEqualTo("updated");
        assertThat(agent.getRole()).isEqualTo("새 역할");
        assertThat(agent.getLlmConfig()).containsEntry("model", "gpt-4");
    }
}
