package com.squad.cli.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squad.cli.api.SquadApiClient;
import com.squad.cli.form.InteractiveFormReader;
import com.squad.cli.shell.CommandContext;
import com.squad.cli.shell.CommandRegistry;
import com.squad.cli.ui.TableRenderer;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Squad 관리 슬래시 커맨드.
 *
 * <p>{@code /squad} 커맨드를 통해 Squad의 목록 조회, 생성, 수정, 삭제를 수행한다.
 * 생성 시 Orchestrator 선택, 멤버 Agent 지정, 직접 통신 규칙 설정을 지원한다.</p>
 */
@Component
public class SquadCommand {

    private static final String API_PATH = "/api/v1/squads";
    private static final String AGENTS_API_PATH = "/api/v1/agents";

    private final SquadApiClient apiClient;
    private final TableRenderer tableRenderer;
    private final InteractiveFormReader formReader;
    private final ObjectMapper objectMapper;

    public SquadCommand(CommandRegistry commandRegistry,
                        SquadApiClient apiClient,
                        TableRenderer tableRenderer,
                        InteractiveFormReader formReader) {
        this.apiClient = apiClient;
        this.tableRenderer = tableRenderer;
        this.formReader = formReader;
        this.objectMapper = new ObjectMapper();
        commandRegistry.register("squad", "Squad 관리 (list/create/update/delete)", this::execute,
                List.of(
                        new CommandRegistry.SubcommandInfo("list", "Squad 목록 조회"),
                        new CommandRegistry.SubcommandInfo("create", "새 Squad 생성"),
                        new CommandRegistry.SubcommandInfo("update", "Squad 수정"),
                        new CommandRegistry.SubcommandInfo("delete", "Squad 삭제")
                ));
    }

    private void execute(CommandContext ctx, String args) {
        String[] parts = args.trim().split("\\s+", 2);
        String subcommand = parts[0];
        String subArgs = parts.length > 1 ? parts[1] : "";

        switch (subcommand) {
            case "list", "" -> handleList(ctx);
            case "create" -> handleCreate(ctx);
            case "update" -> handleUpdate(ctx, subArgs);
            case "delete" -> handleDelete(ctx, subArgs);
            default -> handleDetail(ctx, subcommand);
        }
    }

    private void handleList(CommandContext ctx) {
        PrintWriter writer = ctx.writer();
        Optional<JsonNode> response = apiClient.get(API_PATH);

        if (response.isEmpty()) {
            writer.println("서버에 연결할 수 없습니다.");
            writer.flush();
            return;
        }

        JsonNode data = response.get().get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            writer.println("등록된 Squad가 없습니다.");
            writer.flush();
            return;
        }

        TableRenderer.Builder table = tableRenderer.builder()
                .headers("ID", "이름", "설명", "Orchestrator ID", "멤버 수");

        for (JsonNode squad : data) {
            JsonNode agentIds = squad.get("agentIds");
            int memberCount = (agentIds != null && agentIds.isArray()) ? agentIds.size() : 0;
            table.row(
                    squad.get("id").asText(),
                    squad.get("name").asText(),
                    extractField(squad, "description"),
                    extractField(squad, "orchestratorId"),
                    String.valueOf(memberCount)
            );
        }

        writer.println(table.build());
        writer.flush();
    }

    private void handleCreate(CommandContext ctx) {
        PrintWriter writer = ctx.writer();
        writer.println("=== Squad 생성 ===");
        writer.flush();

        String name = formReader.readLine(ctx, "이름");
        if (name == null) {
            printCancelled(writer);
            return;
        }

        String description = readOptionalLine(ctx, "설명 (선택)");

        // Orchestrator 선택 (ORCHESTRATOR roleType만 필터)
        String orchestratorId = selectOrchestratorInteractively(ctx);
        if (orchestratorId == null) {
            return;
        }

        // 멤버 Agent 선택
        List<Long> agentIds = readAgentIds(ctx, writer, null);
        if (agentIds == null) {
            printCancelled(writer);
            return;
        }

        // 직접 통신 규칙 (선택)
        Map<String, Object> directCommunication = readDirectCommunication(ctx);

        printCreateSummary(writer, name, description, orchestratorId, agentIds, directCommunication);

        if (!formReader.readConfirm(ctx, "생성하시겠습니까?")) {
            printCancelled(writer);
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", description);
        body.put("orchestratorId", Long.parseLong(orchestratorId));
        if (agentIds != null && !agentIds.isEmpty()) {
            body.put("agentIds", agentIds);
        }
        if (directCommunication != null) {
            body.put("directCommunication", directCommunication);
        }

        Optional<JsonNode> response = apiClient.post(API_PATH, body);
        if (response.isPresent()) {
            JsonNode data = response.get().get("data");
            String id = data != null ? data.get("id").asText() : "?";
            writer.println("Squad가 생성되었습니다. (ID: " + id + ")");
        } else {
            writer.println("Squad 생성에 실패했습니다. 서버 연결을 확인해주세요.");
        }
        writer.flush();
    }

    private void handleUpdate(CommandContext ctx, String idArg) {
        PrintWriter writer = ctx.writer();

        String id;
        if (idArg.isBlank()) {
            id = selectSquadInteractively(ctx, "수정할 Squad 선택");
            if (id == null) {
                return;
            }
        } else {
            id = idArg.trim();
        }

        Optional<JsonNode> existing = apiClient.get(API_PATH + "/" + id);
        if (existing.isEmpty()) {
            writer.println("Squad를 찾을 수 없습니다. (ID: " + id + ")");
            writer.flush();
            return;
        }

        JsonNode squadData = existing.get().get("data");
        if (squadData == null) {
            writer.println("Squad 데이터를 읽을 수 없습니다.");
            writer.flush();
            return;
        }

        writer.println("=== Squad 수정 (ID: " + id + ") ===");
        writer.println("(Enter로 기존 값 유지)");
        writer.println("※ Orchestrator는 수정할 수 없습니다.");
        writer.flush();

        String currentName = extractField(squadData, "name");
        String currentDescription = extractField(squadData, "description");

        String name = formReader.readLine(ctx, "이름", currentName);
        if (name == null) {
            printCancelled(writer);
            return;
        }

        String description = readOptionalLine(ctx, "설명", currentDescription);

        // 현재 멤버 표시 및 수정 여부 확인
        JsonNode currentAgentIds = squadData.get("agentIds");
        writer.println("현재 멤버 Agent IDs: " + formatAgentIds(currentAgentIds));
        writer.flush();

        List<Long> agentIds = parseCurrentAgentIds(currentAgentIds);
        if (formReader.readConfirm(ctx, "멤버를 변경하시겠습니까?")) {
            List<Long> newAgentIds = readAgentIds(ctx, writer, agentIds);
            if (newAgentIds == null) {
                printCancelled(writer);
                return;
            }
            agentIds = newAgentIds;
        }

        // 직접 통신 규칙 수정 여부 확인
        JsonNode currentDirectComm = squadData.get("directCommunication");
        if (currentDirectComm != null && !currentDirectComm.isNull()) {
            writer.println("현재 직접 통신 설정:");
            printJsonPretty(writer, currentDirectComm);
            writer.flush();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> directCommunication = (currentDirectComm != null && !currentDirectComm.isNull())
                ? objectMapper.convertValue(currentDirectComm, Map.class)
                : null;
        if (formReader.readConfirm(ctx, "직접 통신 규칙을 변경하시겠습니까?")) {
            directCommunication = readDirectCommunication(ctx);
        }

        if (!formReader.readConfirm(ctx, "수정하시겠습니까?")) {
            printCancelled(writer);
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", description);
        body.put("agentIds", agentIds);
        body.put("directCommunication", directCommunication);

        Optional<JsonNode> response = apiClient.put(API_PATH + "/" + id, body);
        if (response.isPresent()) {
            writer.println("Squad가 수정되었습니다. (ID: " + id + ")");
        } else {
            writer.println("Squad 수정에 실패했습니다.");
        }
        writer.flush();
    }

    private void handleDelete(CommandContext ctx, String idArg) {
        PrintWriter writer = ctx.writer();

        String id;
        String squadName;
        if (idArg.isBlank()) {
            id = selectSquadInteractively(ctx, "삭제할 Squad 선택");
            if (id == null) {
                return;
            }
            Optional<JsonNode> existing = apiClient.get(API_PATH + "/" + id);
            JsonNode squadData = existing.map(r -> r.get("data")).orElse(null);
            squadName = squadData != null ? squadData.get("name").asText() : id;
        } else {
            id = idArg.trim();
            Optional<JsonNode> existing = apiClient.get(API_PATH + "/" + id);
            if (existing.isEmpty()) {
                writer.println("Squad를 찾을 수 없습니다. (ID: " + id + ")");
                writer.flush();
                return;
            }
            JsonNode squadData = existing.get().get("data");
            squadName = squadData != null ? squadData.get("name").asText() : id;
        }

        if (!formReader.readConfirm(ctx, "'" + squadName + "' Squad를 삭제하시겠습니까?")) {
            printCancelled(writer);
            return;
        }

        boolean success = apiClient.delete(API_PATH + "/" + id);
        if (success) {
            writer.println("Squad가 삭제되었습니다. (ID: " + id + ")");
        } else {
            writer.println("Squad 삭제에 실패했습니다.");
        }
        writer.flush();
    }

    private void handleDetail(CommandContext ctx, String idArg) {
        PrintWriter writer = ctx.writer();

        try {
            Long.parseLong(idArg);
        } catch (NumberFormatException e) {
            writer.println("사용법: /squad [list|create|update|delete] 또는 /squad {id}");
            writer.flush();
            return;
        }

        Optional<JsonNode> response = apiClient.get(API_PATH + "/" + idArg);
        if (response.isEmpty()) {
            writer.println("Squad를 찾을 수 없습니다. (ID: " + idArg + ")");
            writer.flush();
            return;
        }

        JsonNode data = response.get().get("data");
        if (data == null) {
            writer.println("Squad 데이터를 읽을 수 없습니다.");
            writer.flush();
            return;
        }

        writer.println("=== Squad 상세 (ID: " + idArg + ") ===");
        writer.println("이름:            " + extractField(data, "name"));
        writer.println("설명:            " + extractField(data, "description"));
        writer.println("Orchestrator ID: " + extractField(data, "orchestratorId"));
        writer.println("멤버 Agent IDs:  " + formatAgentIds(data.get("agentIds")));

        JsonNode directComm = data.get("directCommunication");
        if (directComm != null && !directComm.isNull()) {
            writer.println("직접 통신 설정:");
            printJsonPretty(writer, directComm);
        }

        writer.println("생성일:          " + extractField(data, "createdAt"));
        writer.println("수정일:          " + extractField(data, "updatedAt"));
        writer.flush();
    }

    /**
     * ORCHESTRATOR 역할의 Agent를 조회하여 화살표키 선택 UI로 하나를 고른다.
     *
     * @param ctx 커맨드 컨텍스트
     * @return 선택된 Agent ID 문자열, 취소 또는 목록 없음 시 null
     */
    private String selectOrchestratorInteractively(CommandContext ctx) {
        PrintWriter writer = ctx.writer();
        Optional<JsonNode> response = apiClient.get(AGENTS_API_PATH);

        if (response.isEmpty()) {
            writer.println("서버에 연결할 수 없습니다.");
            writer.flush();
            return null;
        }

        JsonNode data = response.get().get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            writer.println("등록된 에이전트가 없습니다.");
            writer.flush();
            return null;
        }

        List<String> options = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (JsonNode agent : data) {
            String roleType = extractField(agent, "roleType");
            if ("ORCHESTRATOR".equals(roleType)) {
                String agentId = agent.get("id").asText();
                String agentName = agent.get("name").asText();
                ids.add(agentId);
                options.add("[" + agentId + "] " + agentName);
            }
        }

        if (options.isEmpty()) {
            writer.println("ORCHESTRATOR 역할의 에이전트가 없습니다. 먼저 에이전트를 생성해주세요.");
            writer.flush();
            return null;
        }

        int selected = formReader.readSelection(ctx, "Orchestrator 선택", options);
        if (selected < 0) {
            printCancelled(writer);
            return null;
        }
        return ids.get(selected);
    }

    /**
     * 멤버 Agent를 화살표키 + 스페이스바 멀티 선택 UI로 선택받는다.
     *
     * <p>등록된 Agent 목록을 표시하고 스페이스바로 토글, Enter로 확정한다.
     * 기존 선택 Agent ID가 있으면 미리 체크된 상태로 표시한다.</p>
     *
     * @param ctx           커맨드 컨텍스트
     * @param writer        출력용 PrintWriter
     * @param preSelectedIds 미리 선택할 Agent ID 목록 (null 가능)
     * @return Agent ID 리스트, 취소(ESC/Ctrl+C) 시 null
     */
    private List<Long> readAgentIds(CommandContext ctx, PrintWriter writer, List<Long> preSelectedIds) {
        Optional<JsonNode> response = apiClient.get(AGENTS_API_PATH);
        if (response.isEmpty()) {
            writer.println("서버에 연결할 수 없습니다.");
            writer.flush();
            return null;
        }

        JsonNode data = response.get().get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            writer.println("등록된 에이전트가 없습니다.");
            writer.flush();
            return List.of();
        }

        List<String> options = new ArrayList<>();
        List<Long> agentIdList = new ArrayList<>();
        List<Integer> preSelected = new ArrayList<>();

        for (JsonNode agent : data) {
            long agentId = agent.get("id").asLong();
            String agentName = agent.get("name").asText();
            String roleType = extractField(agent, "roleType");
            agentIdList.add(agentId);
            options.add("[" + agentId + "] " + agentName + " (" + roleType + ")");

            if (preSelectedIds != null && preSelectedIds.contains(agentId)) {
                preSelected.add(options.size() - 1);
            }
        }

        List<Integer> selectedIndices = formReader.readMultiSelection(
                ctx, "멤버 Agent 선택", options, preSelected.isEmpty() ? null : preSelected);
        if (selectedIndices == null) {
            return null;
        }

        return selectedIndices.stream()
                .map(agentIdList::get)
                .collect(Collectors.toList());
    }

    /**
     * 직접 통신 규칙 JSON을 입력받는다.
     *
     * <p>빈 입력 시 null을 반환한다 (직접 통신 설정 없음).
     * 최대 3회까지 재시도를 허용한다.</p>
     *
     * @param ctx 커맨드 컨텍스트
     * @return 파싱된 직접 통신 설정 Map, 미입력 시 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readDirectCommunication(CommandContext ctx) {
        PrintWriter writer = ctx.writer();
        writer.println("직접 통신 규칙 예시:");
        writer.println("  {\"enabled\": true, \"rules\": [{\"from\": \"worker-1\", \"to\": \"worker-2\", \"allowed\": true}]}");
        writer.flush();

        int maxAttempts = 3;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String input = formReader.readMultiLine(ctx, "직접 통신 JSON (선택, 빈 줄만 입력하면 건너뜀)");
            if (input == null || input.isEmpty()) {
                return null;
            }

            try {
                JsonNode node = objectMapper.readTree(input);
                if (!node.isObject()) {
                    writer.println("JSON 객체여야 합니다.");
                    writer.flush();
                    continue;
                }
                return objectMapper.convertValue(node, Map.class);
            } catch (JsonProcessingException e) {
                writer.println("JSON 파싱 실패: " + e.getOriginalMessage());
                writer.flush();
            }
        }

        writer.println("입력 시도 횟수를 초과했습니다. 직접 통신 설정을 건너뜁니다.");
        writer.flush();
        return null;
    }

    /**
     * Squad 목록을 조회하여 화살표키 선택 UI로 하나를 고른다.
     *
     * @param ctx    커맨드 컨텍스트
     * @param prompt 선택 프롬프트
     * @return 선택된 Squad ID, 취소 또는 목록 없음 시 null
     */
    private String selectSquadInteractively(CommandContext ctx, String prompt) {
        PrintWriter writer = ctx.writer();
        Optional<JsonNode> response = apiClient.get(API_PATH);

        if (response.isEmpty()) {
            writer.println("서버에 연결할 수 없습니다.");
            writer.flush();
            return null;
        }

        JsonNode data = response.get().get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            writer.println("등록된 Squad가 없습니다.");
            writer.flush();
            return null;
        }

        List<String> options = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (JsonNode squad : data) {
            String squadId = squad.get("id").asText();
            String squadName = squad.get("name").asText();
            String desc = extractField(squad, "description");
            ids.add(squadId);
            String label = "[" + squadId + "] " + squadName;
            if (!desc.isEmpty()) {
                label += " - " + desc;
            }
            options.add(label);
        }

        int selected = formReader.readSelection(ctx, prompt, options);
        if (selected < 0) {
            printCancelled(writer);
            return null;
        }
        return ids.get(selected);
    }

    private void printCreateSummary(PrintWriter writer, String name, String description,
                                     String orchestratorId, List<Long> agentIds,
                                     Map<String, Object> directCommunication) {
        writer.println();
        writer.println("--- 입력 확인 ---");
        writer.println("이름:            " + name);
        writer.println("설명:            " + (description != null ? description : "(없음)"));
        writer.println("Orchestrator ID: " + orchestratorId);
        writer.println("멤버 Agent IDs:  " + (agentIds != null && !agentIds.isEmpty() ? agentIds : "(없음)"));
        writer.println("직접 통신:       " + (directCommunication != null ? "설정됨" : "(없음)"));
        writer.flush();
    }

    private String readOptionalLine(CommandContext ctx, String prompt) {
        return formReader.readLine(ctx, prompt);
    }

    private String readOptionalLine(CommandContext ctx, String prompt, String defaultValue) {
        String effectiveDefault = (defaultValue == null || defaultValue.isEmpty()) ? null : defaultValue;
        return formReader.readLine(ctx, prompt, effectiveDefault);
    }

    private void printCancelled(PrintWriter writer) {
        writer.println("취소되었습니다.");
        writer.flush();
    }

    private void printJsonPretty(PrintWriter writer, JsonNode node) {
        if (node == null || node.isNull()) {
            writer.println("  (없음)");
            return;
        }
        try {
            String pretty = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(node);
            for (String line : pretty.split("\n")) {
                writer.println("  " + line);
            }
        } catch (JsonProcessingException e) {
            writer.println("  " + node);
        }
    }

    private String extractField(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return "";
        }
        return node.get(fieldName).asText();
    }

    private List<Long> parseCurrentAgentIds(JsonNode agentIds) {
        List<Long> result = new ArrayList<>();
        if (agentIds != null && agentIds.isArray()) {
            for (JsonNode idNode : agentIds) {
                result.add(idNode.asLong());
            }
        }
        return result;
    }

    private String formatAgentIds(JsonNode agentIds) {
        if (agentIds == null || !agentIds.isArray() || agentIds.isEmpty()) {
            return "(없음)";
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode idNode : agentIds) {
            ids.add(idNode.asText());
        }
        return String.join(", ", ids);
    }
}
