package com.squad.cli.command;

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

class SkillCommandTest {

    private CommandRegistry registry;
    private SquadApiClient apiClient;
    private TableRenderer tableRenderer;
    private InteractiveFormReader formReader;

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

        new SkillCommand(registry, apiClient, tableRenderer, formReader);

        outputBuffer = new StringWriter();
        writer = new PrintWriter(outputBuffer);
        ctx = new CommandContext(null, null, writer);
    }

    @Test
    @DisplayName("skill 커맨드가 레지스트리에 등록된다")
    void registersSkillCommand() {
        assertThat(registry.find("skill")).isPresent();
        assertThat(registry.find("skill").get().description()).contains("Skill");
    }

    @Test
    @DisplayName("/skill list - Skill 목록을 테이블로 출력한다")
    void listSkills() {
        ObjectNode response = createListResponse(
                createSkill(1L, "요약 Skill", "텍스트 요약", "요약해줘", new long[]{1, 2}),
                createSkill(2L, "번역 Skill", "영한 번역", "번역해줘", new long[]{})
        );
        when(apiClient.get("/api/v1/skills")).thenReturn(Optional.of(response));

        executeCommand("list");

        String output = outputBuffer.toString();
        assertThat(output).contains("요약 Skill");
        assertThat(output).contains("번역 Skill");
    }

    @Test
    @DisplayName("/skill list - Skill이 없으면 안내 메시지를 출력한다")
    void listSkillsEmpty() {
        ObjectNode response = mapper.createObjectNode();
        response.putArray("data");
        when(apiClient.get("/api/v1/skills")).thenReturn(Optional.of(response));

        executeCommand("list");

        assertThat(outputBuffer.toString()).contains("등록된 Skill이 없습니다");
    }

    @Test
    @DisplayName("/skill list - 서버 연결 실패 시 안내 메시지를 출력한다")
    void listSkillsServerError() {
        when(apiClient.get("/api/v1/skills")).thenReturn(Optional.empty());

        executeCommand("list");

        assertThat(outputBuffer.toString()).contains("서버에 연결할 수 없습니다");
    }

    @Test
    @DisplayName("/skill {id} - Skill 상세 정보를 출력한다")
    void showSkillDetail() {
        ObjectNode response = createDetailResponse(1L, "요약 Skill", "텍스트 요약",
                "요약해줘", new long[]{1, 2});
        when(apiClient.get("/api/v1/skills/1")).thenReturn(Optional.of(response));

        executeCommand("1");

        String output = outputBuffer.toString();
        assertThat(output).contains("Skill 상세");
        assertThat(output).contains("요약 Skill");
        assertThat(output).contains("요약해줘");
    }

    @Test
    @DisplayName("/skill {id} - 존재하지 않는 Skill 조회 시 안내 메시지를 출력한다")
    void showSkillDetailNotFound() {
        when(apiClient.get("/api/v1/skills/999")).thenReturn(Optional.empty());

        executeCommand("999");

        assertThat(outputBuffer.toString()).contains("Skill을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("/skill create - Skill을 생성한다")
    void createSkill() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn("요약 Skill");
        when(formReader.readLine(eq(ctx), eq("설명 (선택)"))).thenReturn("텍스트 요약");

        // 프롬프트: 직접 입력 선택(인덱스 1)
        when(formReader.readSelection(eq(ctx), eq("프롬프트 입력 방법"), any())).thenReturn(1);
        when(formReader.readMultiLine(eq(ctx), eq("프롬프트"))).thenReturn("다음 텍스트를 요약해줘");

        // MCP 목록 빈 상태
        ObjectNode mcpResponse = mapper.createObjectNode();
        mcpResponse.putArray("data");
        when(apiClient.get("/api/v1/mcps")).thenReturn(Optional.of(mcpResponse));

        when(formReader.readConfirm(any(), any())).thenReturn(true);

        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ObjectNode data = mapper.createObjectNode();
        data.put("id", 1);
        response.set("data", data);
        when(apiClient.post(eq("/api/v1/skills"), any())).thenReturn(Optional.of(response));

        executeCommand("create");

        verify(apiClient).post(eq("/api/v1/skills"), any());
        assertThat(outputBuffer.toString()).contains("Skill이 생성되었습니다");
    }

    @Test
    @DisplayName("/skill create - 이름 입력 취소 시 생성을 중단한다")
    void createSkillCancelledAtName() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn(null);

        executeCommand("create");

        verify(apiClient, never()).post(any(), any());
        assertThat(outputBuffer.toString()).contains("취소되었습니다");
    }

    @Test
    @DisplayName("/skill create - 프롬프트 입력 취소 시 생성을 중단한다")
    void createSkillCancelledAtPrompt() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn("Skill");
        when(formReader.readLine(eq(ctx), eq("설명 (선택)"))).thenReturn(null);
        // 프롬프트 선택 취소
        when(formReader.readSelection(eq(ctx), eq("프롬프트 입력 방법"), any())).thenReturn(-1);

        executeCommand("create");

        verify(apiClient, never()).post(any(), any());
        assertThat(outputBuffer.toString()).contains("취소되었습니다");
    }

    @Test
    @DisplayName("/skill create - requiredMcps를 멀티 선택으로 지정한다")
    void createSkillWithRequiredMcps() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn("MCP Skill");
        when(formReader.readLine(eq(ctx), eq("설명 (선택)"))).thenReturn(null);
        when(formReader.readSelection(eq(ctx), eq("프롬프트 입력 방법"), any())).thenReturn(1);
        when(formReader.readMultiLine(eq(ctx), eq("프롬프트"))).thenReturn("프롬프트");

        // MCP 목록
        ObjectNode mcpResponse = createMcpListResponse(
                createMcp(1L, "GitHub MCP"),
                createMcp(2L, "Slack MCP")
        );
        when(apiClient.get("/api/v1/mcps")).thenReturn(Optional.of(mcpResponse));
        when(formReader.readMultiSelection(eq(ctx), eq("필요 MCP 선택 (선택사항)"), any(), any()))
                .thenReturn(List.of(0, 1));

        when(formReader.readConfirm(any(), any())).thenReturn(true);

        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ObjectNode data = mapper.createObjectNode();
        data.put("id", 1);
        response.set("data", data);
        when(apiClient.post(eq("/api/v1/skills"), any())).thenReturn(Optional.of(response));

        executeCommand("create");

        verify(apiClient).post(eq("/api/v1/skills"), any());
        assertThat(outputBuffer.toString()).contains("Skill이 생성되었습니다");
    }

    @Test
    @DisplayName("/skill delete {id} - 확인 후 Skill을 삭제한다")
    void deleteSkill() {
        ObjectNode response = createDetailResponse(1L, "요약 Skill", "설명",
                "프롬프트", new long[]{});
        when(apiClient.get("/api/v1/skills/1")).thenReturn(Optional.of(response));
        when(apiClient.delete("/api/v1/skills/1")).thenReturn(true);
        when(formReader.readConfirm(any(), any())).thenReturn(true);

        executeCommand("delete 1");

        verify(apiClient).delete("/api/v1/skills/1");
        assertThat(outputBuffer.toString()).contains("Skill이 삭제되었습니다");
    }

    @Test
    @DisplayName("/skill delete {id} - 취소하면 삭제하지 않는다")
    void deleteSkillCancelled() {
        ObjectNode response = createDetailResponse(1L, "요약 Skill", "설명",
                "프롬프트", new long[]{});
        when(apiClient.get("/api/v1/skills/1")).thenReturn(Optional.of(response));
        when(formReader.readConfirm(any(), any())).thenReturn(false);

        executeCommand("delete 1");

        verify(apiClient, never()).delete(any());
        assertThat(outputBuffer.toString()).contains("취소되었습니다");
    }

    @Test
    @DisplayName("/skill delete {id} - 존재하지 않는 Skill 삭제 시 안내 메시지를 출력한다")
    void deleteSkillNotFound() {
        when(apiClient.get("/api/v1/skills/999")).thenReturn(Optional.empty());

        executeCommand("delete 999");

        assertThat(outputBuffer.toString()).contains("Skill을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("/skill update - ID 없이 호출하면 Skill 목록에서 선택한다")
    void updateSkillNoId() {
        when(apiClient.get("/api/v1/skills")).thenReturn(Optional.empty());

        executeCommand("update");

        assertThat(outputBuffer.toString()).contains("서버에 연결할 수 없습니다");
    }

    @Test
    @DisplayName("잘못된 서브커맨드는 사용법을 안내한다")
    void invalidSubcommand() {
        executeCommand("invalid");

        assertThat(outputBuffer.toString()).contains("사용법");
    }

    @Test
    @DisplayName("/skill (빈 인자)는 list와 동일하게 동작한다")
    void emptyArgsDefaultsToList() {
        ObjectNode response = createListResponse(
                createSkill(1L, "요약 Skill", "텍스트 요약", "프롬프트", new long[]{})
        );
        when(apiClient.get("/api/v1/skills")).thenReturn(Optional.of(response));

        executeCommand("");

        assertThat(outputBuffer.toString()).contains("요약 Skill");
    }

    private void executeCommand(String args) {
        registry.find("skill").get().executor().execute(ctx, args);
    }

    private ObjectNode createSkill(Long id, String name, String description,
                                    String prompt, long[] requiredMcps) {
        ObjectNode skill = mapper.createObjectNode();
        skill.put("id", id);
        skill.put("name", name);
        skill.put("description", description);
        skill.put("prompt", prompt);
        ArrayNode mcps = skill.putArray("requiredMcps");
        for (long mcpId : requiredMcps) {
            mcps.add(mcpId);
        }
        return skill;
    }

    private ObjectNode createListResponse(ObjectNode... skills) {
        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ArrayNode data = response.putArray("data");
        for (ObjectNode skill : skills) {
            data.add(skill);
        }
        return response;
    }

    private ObjectNode createDetailResponse(Long id, String name, String description,
                                             String prompt, long[] requiredMcps) {
        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ObjectNode data = mapper.createObjectNode();
        data.put("id", id);
        data.put("name", name);
        data.put("description", description);
        data.put("prompt", prompt);
        ArrayNode mcps = data.putArray("requiredMcps");
        for (long mcpId : requiredMcps) {
            mcps.add(mcpId);
        }
        data.put("createdAt", "2026-02-16T10:00:00");
        data.put("updatedAt", "2026-02-16T10:00:00");
        response.set("data", data);
        return response;
    }

    private ObjectNode createMcp(Long id, String name) {
        ObjectNode mcp = mapper.createObjectNode();
        mcp.put("id", id);
        mcp.put("name", name);
        return mcp;
    }

    private ObjectNode createMcpListResponse(ObjectNode... mcps) {
        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ArrayNode data = response.putArray("data");
        for (ObjectNode mcp : mcps) {
            data.add(mcp);
        }
        return response;
    }
}
