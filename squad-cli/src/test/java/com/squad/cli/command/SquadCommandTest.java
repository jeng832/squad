package com.squad.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.squad.cli.api.SquadApiClient;
import com.squad.cli.form.InteractiveFormReader;
import com.squad.cli.shell.CommandContext;
import com.squad.cli.shell.CommandRegistry;
import com.squad.cli.ui.TableRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SquadCommandTest {

    private CommandRegistry registry;
    private SquadApiClient apiClient;
    private TableRenderer tableRenderer;
    private InteractiveFormReader formReader;
    private SquadCommand squadCommand;

    private StringWriter outputBuffer;
    private PrintWriter writer;
    private CommandContext ctx;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry();
        apiClient = mock(SquadApiClient.class);
        tableRenderer = new TableRenderer();
        formReader = mock(InteractiveFormReader.class);

        squadCommand = new SquadCommand(registry, apiClient, tableRenderer, formReader);

        outputBuffer = new StringWriter();
        writer = new PrintWriter(outputBuffer);
        ctx = new CommandContext(null, null, writer);
    }

    @Test
    @DisplayName("squad 커맨드가 레지스트리에 등록된다")
    void registersSquadCommand() {
        assertThat(registry.find("squad")).isPresent();
        assertThat(registry.find("squad").get().description()).contains("Squad");
    }

    @Test
    @DisplayName("/squad list - Squad 목록을 테이블로 출력한다")
    void listSquads() {
        ObjectNode response = createListResponse(
                createSquad(1L, "코드 리뷰 팀", "코드 리뷰 수행", 10L, new long[]{20L, 30L}),
                createSquad(2L, "분석 팀", "데이터 분석", 11L, new long[]{})
        );
        when(apiClient.get("/api/v1/squads")).thenReturn(Optional.of(response));

        executeCommand("list");

        String output = outputBuffer.toString();
        assertThat(output).contains("코드 리뷰 팀");
        assertThat(output).contains("분석 팀");
        assertThat(output).contains("10");
    }

    @Test
    @DisplayName("/squad list - Squad가 없으면 안내 메시지를 출력한다")
    void listSquadsEmpty() {
        ObjectNode response = mapper.createObjectNode();
        response.putArray("data");
        when(apiClient.get("/api/v1/squads")).thenReturn(Optional.of(response));

        executeCommand("list");

        assertThat(outputBuffer.toString()).contains("등록된 Squad가 없습니다");
    }

    @Test
    @DisplayName("/squad list - 서버 연결 실패 시 안내 메시지를 출력한다")
    void listSquadsServerError() {
        when(apiClient.get("/api/v1/squads")).thenReturn(Optional.empty());

        executeCommand("list");

        assertThat(outputBuffer.toString()).contains("서버에 연결할 수 없습니다");
    }

    @Test
    @DisplayName("/squad {id} - Squad 상세 정보를 출력한다")
    void showSquadDetail() {
        ObjectNode response = createDetailResponse(1L, "코드 리뷰 팀", "코드 리뷰 수행",
                10L, new long[]{20L, 30L}, null);
        when(apiClient.get("/api/v1/squads/1")).thenReturn(Optional.of(response));

        executeCommand("1");

        String output = outputBuffer.toString();
        assertThat(output).contains("Squad 상세");
        assertThat(output).contains("코드 리뷰 팀");
        assertThat(output).contains("코드 리뷰 수행");
        assertThat(output).contains("10");
        assertThat(output).contains("20");
        assertThat(output).contains("30");
    }

    @Test
    @DisplayName("/squad {id} - 직접 통신 설정이 있으면 표시한다")
    void showSquadDetailWithDirectCommunication() {
        ObjectNode directComm = mapper.createObjectNode();
        directComm.put("enabled", true);
        ArrayNode rules = directComm.putArray("rules");
        ObjectNode rule = mapper.createObjectNode();
        rule.put("from", "worker-1");
        rule.put("to", "worker-2");
        rule.put("allowed", true);
        rules.add(rule);

        ObjectNode response = createDetailResponse(1L, "팀", "설명",
                10L, new long[]{}, directComm);
        when(apiClient.get("/api/v1/squads/1")).thenReturn(Optional.of(response));

        executeCommand("1");

        String output = outputBuffer.toString();
        assertThat(output).contains("직접 통신 설정");
        assertThat(output).contains("worker-1");
    }

    @Test
    @DisplayName("/squad {id} - 존재하지 않는 Squad 조회 시 안내 메시지를 출력한다")
    void showSquadDetailNotFound() {
        when(apiClient.get("/api/v1/squads/999")).thenReturn(Optional.empty());

        executeCommand("999");

        assertThat(outputBuffer.toString()).contains("Squad를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("/squad create - Orchestrator 선택 후 Squad를 생성한다")
    void createSquad() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn("새 팀");
        when(formReader.readLine(eq(ctx), eq("설명 (선택)"))).thenReturn("팀 설명");

        // Orchestrator 선택: Agent 목록 반환 (ORCHESTRATOR 1개)
        ObjectNode agentListResponse = createAgentListResponse(
                createAgent(10L, "orchestrator-1", "ORCHESTRATOR"),
                createAgent(20L, "worker-1", "WORKER")
        );
        when(apiClient.get("/api/v1/agents")).thenReturn(Optional.of(agentListResponse));
        when(formReader.readSelection(eq(ctx), eq("Orchestrator 선택"), any())).thenReturn(0);

        // 멤버 선택 (인덱스 1 = worker-1, ID: 20)
        when(formReader.readMultiSelection(eq(ctx), eq("멤버 Agent 선택"), any(), any())).thenReturn(List.of(1));

        // 직접 통신 건너뜀
        when(formReader.readMultiLine(eq(ctx), any())).thenReturn("");

        // 확인
        when(formReader.readConfirm(any(), any())).thenReturn(true);

        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ObjectNode data = mapper.createObjectNode();
        data.put("id", 1);
        response.set("data", data);
        when(apiClient.post(eq("/api/v1/squads"), any())).thenReturn(Optional.of(response));

        executeCommand("create");

        verify(apiClient).post(eq("/api/v1/squads"), any());
        assertThat(outputBuffer.toString()).contains("Squad가 생성되었습니다");
    }

    @Test
    @DisplayName("/squad create - 이름 입력 취소 시 생성을 중단한다")
    void createSquadCancelledAtName() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn(null);

        executeCommand("create");

        verify(apiClient, never()).post(any(), any());
        assertThat(outputBuffer.toString()).contains("취소되었습니다");
    }

    @Test
    @DisplayName("/squad create - ORCHESTRATOR 에이전트가 없으면 안내한다")
    void createSquadNoOrchestrator() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn("팀");
        when(formReader.readLine(eq(ctx), eq("설명 (선택)"))).thenReturn(null);

        // ORCHESTRATOR가 없는 Agent 목록
        ObjectNode agentListResponse = createAgentListResponse(
                createAgent(20L, "worker-1", "WORKER")
        );
        when(apiClient.get("/api/v1/agents")).thenReturn(Optional.of(agentListResponse));

        executeCommand("create");

        verify(apiClient, never()).post(any(), any());
        assertThat(outputBuffer.toString()).contains("ORCHESTRATOR 역할의 에이전트가 없습니다");
    }

    @Test
    @DisplayName("/squad create - Orchestrator 선택 취소 시 생성을 중단한다")
    void createSquadOrchestratorCancelled() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn("팀");
        when(formReader.readLine(eq(ctx), eq("설명 (선택)"))).thenReturn(null);

        ObjectNode agentListResponse = createAgentListResponse(
                createAgent(10L, "orchestrator-1", "ORCHESTRATOR")
        );
        when(apiClient.get("/api/v1/agents")).thenReturn(Optional.of(agentListResponse));
        when(formReader.readSelection(eq(ctx), eq("Orchestrator 선택"), any())).thenReturn(-1);

        executeCommand("create");

        verify(apiClient, never()).post(any(), any());
        assertThat(outputBuffer.toString()).contains("취소되었습니다");
    }

    @Test
    @DisplayName("/squad create - 멤버 선택 취소 시 생성을 취소한다")
    void createSquadMemberSelectionCancelled() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn("팀");
        when(formReader.readLine(eq(ctx), eq("설명 (선택)"))).thenReturn(null);

        ObjectNode agentListResponse = createAgentListResponse(
                createAgent(10L, "orchestrator-1", "ORCHESTRATOR"),
                createAgent(20L, "worker-1", "WORKER")
        );
        when(apiClient.get("/api/v1/agents")).thenReturn(Optional.of(agentListResponse));
        when(formReader.readSelection(eq(ctx), eq("Orchestrator 선택"), any())).thenReturn(0);

        // 멤버 선택 취소 (ESC/Ctrl+C)
        when(formReader.readMultiSelection(eq(ctx), eq("멤버 Agent 선택"), any(), any())).thenReturn(null);

        executeCommand("create");

        verify(apiClient, never()).post(any(), any());
        assertThat(outputBuffer.toString()).contains("취소되었습니다");
    }

    @Test
    @DisplayName("/squad delete {id} - 확인 후 Squad를 삭제한다")
    void deleteSquad() {
        ObjectNode response = createDetailResponse(1L, "팀", "설명",
                10L, new long[]{}, null);
        when(apiClient.get("/api/v1/squads/1")).thenReturn(Optional.of(response));
        when(apiClient.delete("/api/v1/squads/1")).thenReturn(true);
        when(formReader.readConfirm(any(), any())).thenReturn(true);

        executeCommand("delete 1");

        verify(apiClient).delete("/api/v1/squads/1");
        assertThat(outputBuffer.toString()).contains("Squad가 삭제되었습니다");
    }

    @Test
    @DisplayName("/squad delete {id} - 취소하면 삭제하지 않는다")
    void deleteSquadCancelled() {
        ObjectNode response = createDetailResponse(1L, "팀", "설명",
                10L, new long[]{}, null);
        when(apiClient.get("/api/v1/squads/1")).thenReturn(Optional.of(response));
        when(formReader.readConfirm(any(), any())).thenReturn(false);

        executeCommand("delete 1");

        verify(apiClient, never()).delete(any());
        assertThat(outputBuffer.toString()).contains("취소되었습니다");
    }

    @Test
    @DisplayName("/squad delete - ID 없이 호출하면 Squad 목록에서 선택한다")
    void deleteSquadNoId() {
        when(apiClient.get("/api/v1/squads")).thenReturn(Optional.empty());

        executeCommand("delete");

        assertThat(outputBuffer.toString()).contains("서버에 연결할 수 없습니다");
    }

    @Test
    @DisplayName("/squad delete {id} - 존재하지 않는 Squad 삭제 시 안내 메시지를 출력한다")
    void deleteSquadNotFound() {
        when(apiClient.get("/api/v1/squads/999")).thenReturn(Optional.empty());

        executeCommand("delete 999");

        assertThat(outputBuffer.toString()).contains("Squad를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("/squad delete {id} - 삭제 실패 시 안내 메시지를 출력한다")
    void deleteSquadFailed() {
        ObjectNode response = createDetailResponse(1L, "팀", "설명",
                10L, new long[]{}, null);
        when(apiClient.get("/api/v1/squads/1")).thenReturn(Optional.of(response));
        when(apiClient.delete("/api/v1/squads/1")).thenReturn(false);
        when(formReader.readConfirm(any(), any())).thenReturn(true);

        executeCommand("delete 1");

        assertThat(outputBuffer.toString()).contains("삭제에 실패했습니다");
    }

    @Test
    @DisplayName("/squad update - ID 없이 호출하면 Squad 목록에서 선택한다")
    void updateSquadNoId() {
        when(apiClient.get("/api/v1/squads")).thenReturn(Optional.empty());

        executeCommand("update");

        assertThat(outputBuffer.toString()).contains("서버에 연결할 수 없습니다");
    }

    @Test
    @DisplayName("/squad update {id} - 이름과 설명을 수정할 수 있다")
    void updateSquad() {
        ObjectNode response = createDetailResponse(1L, "기존 팀", "기존 설명",
                10L, new long[]{20L}, null);
        when(apiClient.get("/api/v1/squads/1")).thenReturn(Optional.of(response));

        when(formReader.readLine(eq(ctx), eq("이름"), eq("기존 팀"))).thenReturn("수정 팀");
        when(formReader.readLine(eq(ctx), eq("설명"), eq("기존 설명"))).thenReturn("수정 설명");

        // 멤버 변경 안함, 직접 통신 변경 안함, 수정 확인
        when(formReader.readConfirm(any(), eq("멤버를 변경하시겠습니까?"))).thenReturn(false);
        when(formReader.readConfirm(any(), eq("직접 통신 규칙을 변경하시겠습니까?"))).thenReturn(false);
        when(formReader.readConfirm(any(), eq("수정하시겠습니까?"))).thenReturn(true);

        ObjectNode updateResponse = mapper.createObjectNode();
        updateResponse.put("success", true);
        when(apiClient.put(eq("/api/v1/squads/1"), any())).thenReturn(Optional.of(updateResponse));

        executeCommand("update 1");

        verify(apiClient).put(eq("/api/v1/squads/1"), any());
        assertThat(outputBuffer.toString()).contains("Squad가 수정되었습니다");
    }

    @Test
    @DisplayName("잘못된 서브커맨드는 사용법을 안내한다")
    void invalidSubcommand() {
        executeCommand("invalid");

        assertThat(outputBuffer.toString()).contains("사용법");
    }

    @Test
    @DisplayName("/squad (빈 인자)는 list와 동일하게 동작한다")
    void emptyArgsDefaultsToList() {
        ObjectNode response = createListResponse(
                createSquad(1L, "팀", "설명", 10L, new long[]{})
        );
        when(apiClient.get("/api/v1/squads")).thenReturn(Optional.of(response));

        executeCommand("");

        assertThat(outputBuffer.toString()).contains("팀");
    }

    @Test
    @DisplayName("/squad create - 에이전트 서버 연결 실패 시 Orchestrator 선택을 중단한다")
    void createSquadAgentServerError() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn("팀");
        when(formReader.readLine(eq(ctx), eq("설명 (선택)"))).thenReturn(null);
        when(apiClient.get("/api/v1/agents")).thenReturn(Optional.empty());

        executeCommand("create");

        verify(apiClient, never()).post(any(), any());
        assertThat(outputBuffer.toString()).contains("서버에 연결할 수 없습니다");
    }

    @Test
    @DisplayName("/squad update {id} - 존재하지 않는 Squad 수정 시 안내 메시지를 출력한다")
    void updateSquadNotFound() {
        when(apiClient.get("/api/v1/squads/999")).thenReturn(Optional.empty());

        executeCommand("update 999");

        assertThat(outputBuffer.toString()).contains("Squad를 찾을 수 없습니다");
    }

    private void executeCommand(String args) {
        registry.find("squad").get().executor().execute(ctx, args);
    }

    private ObjectNode createSquad(Long id, String name, String description,
                                    Long orchestratorId, long[] agentIds) {
        ObjectNode squad = mapper.createObjectNode();
        squad.put("id", id);
        squad.put("name", name);
        squad.put("description", description);
        squad.put("orchestratorId", orchestratorId);
        ArrayNode ids = squad.putArray("agentIds");
        for (long agentId : agentIds) {
            ids.add(agentId);
        }
        return squad;
    }

    private ObjectNode createListResponse(ObjectNode... squads) {
        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ArrayNode data = response.putArray("data");
        for (ObjectNode squad : squads) {
            data.add(squad);
        }
        return response;
    }

    private ObjectNode createDetailResponse(Long id, String name, String description,
                                             Long orchestratorId, long[] agentIds,
                                             ObjectNode directCommunication) {
        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ObjectNode data = mapper.createObjectNode();
        data.put("id", id);
        data.put("name", name);
        data.put("description", description);
        data.put("orchestratorId", orchestratorId);
        ArrayNode ids = data.putArray("agentIds");
        for (long agentId : agentIds) {
            ids.add(agentId);
        }
        if (directCommunication != null) {
            data.set("directCommunication", directCommunication);
        }
        data.put("createdAt", "2026-02-16T10:00:00");
        data.put("updatedAt", "2026-02-16T10:00:00");
        response.set("data", data);
        return response;
    }

    private ObjectNode createAgent(Long id, String name, String roleType) {
        ObjectNode agent = mapper.createObjectNode();
        agent.put("id", id);
        agent.put("name", name);
        agent.put("roleType", roleType);
        ObjectNode llmConfig = mapper.createObjectNode();
        llmConfig.put("model", "claude-sonnet-4-20250514");
        agent.set("llmConfig", llmConfig);
        return agent;
    }

    private ObjectNode createAgentListResponse(ObjectNode... agents) {
        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ArrayNode data = response.putArray("data");
        for (ObjectNode agent : agents) {
            data.add(agent);
        }
        return response;
    }
}
