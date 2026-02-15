package com.squad.squad.service;

import com.squad.agent.domain.Agent;
import com.squad.agent.domain.RoleType;
import com.squad.agent.repository.AgentRepository;
import com.squad.common.exception.NotFoundException;
import com.squad.common.exception.ValidationException;
import com.squad.squad.domain.Squad;
import com.squad.squad.dto.SquadCreateRequest;
import com.squad.squad.dto.SquadResponse;
import com.squad.squad.dto.SquadUpdateRequest;
import com.squad.squad.repository.SquadRepository;
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
@DisplayName("SquadService 단위 테스트")
class SquadServiceTest {

    @Mock
    private SquadRepository squadRepository;

    @Mock
    private AgentRepository agentRepository;

    @InjectMocks
    private SquadService squadService;

    private Agent createAgent(Long id, String name, RoleType roleType) {
        return Agent.builder()
                .id(id)
                .name(name)
                .roleType(roleType)
                .role("테스트 역할")
                .llmConfig(Map.of("provider", "claude"))
                .build();
    }

    private Squad createSquad(Long id, String name, Agent orchestrator) {
        return Squad.builder()
                .id(id)
                .name(name)
                .description("테스트 Squad")
                .orchestrator(orchestrator)
                .build();
    }

    @Test
    @DisplayName("전체 Squad 목록을 조회한다")
    void findAll() {
        Agent orchestrator = createAgent(1L, "orch", RoleType.ORCHESTRATOR);
        given(squadRepository.findAll()).willReturn(List.of(
                createSquad(1L, "squad-1", orchestrator),
                createSquad(2L, "squad-2", orchestrator)
        ));

        List<SquadResponse> result = squadService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("squad-1");
    }

    @Test
    @DisplayName("빈 목록 조회 시 빈 리스트를 반환한다")
    void findAllEmpty() {
        given(squadRepository.findAll()).willReturn(List.of());

        assertThat(squadService.findAll()).isEmpty();
    }

    @Test
    @DisplayName("ID로 Squad를 조회한다")
    void findById() {
        Agent orchestrator = createAgent(1L, "orch", RoleType.ORCHESTRATOR);
        given(squadRepository.findById(1L)).willReturn(Optional.of(createSquad(1L, "squad-1", orchestrator)));

        SquadResponse result = squadService.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("squad-1");
        assertThat(result.orchestratorId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회 시 NotFoundException 발생")
    void findByIdNotFound() {
        given(squadRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> squadService.findById(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Squad를 생성한다")
    void create() {
        Agent orchestrator = createAgent(1L, "orch", RoleType.ORCHESTRATOR);
        given(agentRepository.findById(1L)).willReturn(Optional.of(orchestrator));
        Squad saved = createSquad(1L, "new-squad", orchestrator);
        given(squadRepository.save(any(Squad.class))).willReturn(saved);

        SquadCreateRequest request = new SquadCreateRequest("new-squad", "설명", 1L, null, null);
        SquadResponse result = squadService.create(request);

        assertThat(result.name()).isEqualTo("new-squad");
        assertThat(result.orchestratorId()).isEqualTo(1L);
        verify(squadRepository).save(any(Squad.class));
    }

    @Test
    @DisplayName("Squad 생성 시 agentIds로 멤버를 추가한다")
    void createWithAgents() {
        Agent orchestrator = createAgent(1L, "orch", RoleType.ORCHESTRATOR);
        Agent worker = createAgent(2L, "worker", RoleType.WORKER);
        given(agentRepository.findById(1L)).willReturn(Optional.of(orchestrator));
        given(agentRepository.findById(2L)).willReturn(Optional.of(worker));
        Squad saved = createSquad(1L, "squad", orchestrator);
        given(squadRepository.save(any(Squad.class))).willReturn(saved);

        SquadCreateRequest request = new SquadCreateRequest("squad", "설명", 1L, List.of(2L), null);
        SquadResponse result = squadService.create(request);

        assertThat(result.name()).isEqualTo("squad");
    }

    @Test
    @DisplayName("존재하지 않는 orchestratorId로 생성 시 NotFoundException 발생")
    void createOrchestratorNotFound() {
        given(agentRepository.findById(99L)).willReturn(Optional.empty());

        SquadCreateRequest request = new SquadCreateRequest("squad", "설명", 99L, null, null);

        assertThatThrownBy(() -> squadService.create(request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("ORCHESTRATOR가 아닌 Agent를 orchestrator로 지정하면 ValidationException 발생")
    void createWithWorkerAsOrchestrator() {
        Agent worker = createAgent(1L, "worker", RoleType.WORKER);
        given(agentRepository.findById(1L)).willReturn(Optional.of(worker));

        SquadCreateRequest request = new SquadCreateRequest("squad", "설명", 1L, null, null);

        assertThatThrownBy(() -> squadService.create(request))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Squad 생성 시 존재하지 않는 agentId가 있으면 NotFoundException 발생")
    void createWithNonExistentAgent() {
        Agent orchestrator = createAgent(1L, "orch", RoleType.ORCHESTRATOR);
        given(agentRepository.findById(1L)).willReturn(Optional.of(orchestrator));
        given(agentRepository.findById(99L)).willReturn(Optional.empty());
        given(squadRepository.save(any(Squad.class))).willReturn(createSquad(1L, "squad", orchestrator));

        SquadCreateRequest request = new SquadCreateRequest("squad", "설명", 1L, List.of(99L), null);

        assertThatThrownBy(() -> squadService.create(request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Squad를 수정한다")
    void update() {
        Agent orchestrator = createAgent(1L, "orch", RoleType.ORCHESTRATOR);
        Squad squad = createSquad(1L, "squad-1", orchestrator);
        given(squadRepository.findById(1L)).willReturn(Optional.of(squad));

        SquadUpdateRequest request = new SquadUpdateRequest("updated", "새 설명", null, null);
        SquadResponse result = squadService.update(1L, request);

        assertThat(result.name()).isEqualTo("updated");
    }

    @Test
    @DisplayName("Squad 수정 시 agentIds로 멤버를 교체한다")
    void updateWithAgents() {
        Agent orchestrator = createAgent(1L, "orch", RoleType.ORCHESTRATOR);
        Agent worker = createAgent(3L, "worker-new", RoleType.WORKER);
        Squad squad = createSquad(1L, "squad-1", orchestrator);
        given(squadRepository.findById(1L)).willReturn(Optional.of(squad));
        given(agentRepository.findById(3L)).willReturn(Optional.of(worker));

        SquadUpdateRequest request = new SquadUpdateRequest("squad-1", "설명", List.of(3L), null);
        SquadResponse result = squadService.update(1L, request);

        assertThat(result.agentIds()).contains(3L);
    }

    @Test
    @DisplayName("존재하지 않는 Squad 수정 시 NotFoundException 발생")
    void updateNotFound() {
        given(squadRepository.findById(99L)).willReturn(Optional.empty());
        SquadUpdateRequest request = new SquadUpdateRequest("name", "설명", null, null);

        assertThatThrownBy(() -> squadService.update(99L, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Squad 수정 시 존재하지 않는 agentId가 있으면 NotFoundException 발생")
    void updateWithNonExistentAgent() {
        Agent orchestrator = createAgent(1L, "orch", RoleType.ORCHESTRATOR);
        Squad squad = createSquad(1L, "squad-1", orchestrator);
        given(squadRepository.findById(1L)).willReturn(Optional.of(squad));
        given(agentRepository.findById(99L)).willReturn(Optional.empty());

        SquadUpdateRequest request = new SquadUpdateRequest("squad-1", "설명", List.of(99L), null);

        assertThatThrownBy(() -> squadService.update(1L, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Squad를 삭제한다")
    void delete() {
        Agent orchestrator = createAgent(1L, "orch", RoleType.ORCHESTRATOR);
        given(squadRepository.findById(1L)).willReturn(Optional.of(createSquad(1L, "squad-1", orchestrator)));

        squadService.delete(1L);

        verify(squadRepository).deleteById(1L);
    }

    @Test
    @DisplayName("존재하지 않는 Squad 삭제 시 NotFoundException 발생")
    void deleteNotFound() {
        given(squadRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> squadService.delete(99L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Squad 생성 시 directCommunication 설정이 저장된다")
    void createWithDirectCommunication() {
        Agent orchestrator = createAgent(1L, "orch", RoleType.ORCHESTRATOR);
        given(agentRepository.findById(1L)).willReturn(Optional.of(orchestrator));
        Squad saved = Squad.builder()
                .id(1L).name("squad").orchestrator(orchestrator)
                .directCommunication(Map.of("enabled", true))
                .build();
        given(squadRepository.save(any(Squad.class))).willReturn(saved);

        SquadCreateRequest request = new SquadCreateRequest(
                "squad", "설명", 1L, null, Map.of("enabled", true));
        SquadResponse result = squadService.create(request);

        assertThat(result.directCommunication()).containsEntry("enabled", true);
    }
}
