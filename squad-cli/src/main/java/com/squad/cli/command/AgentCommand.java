package com.squad.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.squad.cli.api.SquadApiClient;
import com.squad.cli.form.InteractiveFormReader;
import com.squad.cli.shell.CommandContext;
import com.squad.cli.shell.CommandRegistry;
import com.squad.cli.ui.TableRenderer;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 에이전트 관리 슬래시 커맨드.
 *
 * <p>{@code /agent} 커맨드를 통해 에이전트의 목록 조회, 생성, 수정, 삭제를 수행한다.
 * 생성/수정 시 대화형 가이드 폼을 제공한다.</p>
 */
@Component
public class AgentCommand {

    private static final String API_PATH = "/api/v1/agents";

    private static final List<String> ROLE_TYPE_OPTIONS = List.of(
            "ORCHESTRATOR - 작업 분배 및 조율",
            "WORKER - 실제 작업 수행",
            "ANALYST - 결과 분석 및 종합",
            "SCRIBE - 과정 기록 및 문서화",
            "CUSTOM - 사용자 정의 역할"
    );

    private static final List<String> ROLE_TYPE_VALUES = List.of(
            "ORCHESTRATOR", "WORKER", "ANALYST", "SCRIBE", "CUSTOM"
    );

    private final SquadApiClient apiClient;
    private final TableRenderer tableRenderer;
    private final InteractiveFormReader formReader;

    public AgentCommand(CommandRegistry commandRegistry,
                        SquadApiClient apiClient,
                        TableRenderer tableRenderer,
                        InteractiveFormReader formReader) {
        this.apiClient = apiClient;
        this.tableRenderer = tableRenderer;
        this.formReader = formReader;
        commandRegistry.register("agent", "에이전트 관리 (list/create/update/delete)", this::execute);
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
            writer.println("등록된 에이전트가 없습니다.");
            writer.flush();
            return;
        }

        TableRenderer.Builder table = tableRenderer.builder()
                .headers("ID", "이름", "역할 유형", "LLM 모델");

        for (JsonNode agent : data) {
            table.row(
                    agent.get("id").asText(),
                    agent.get("name").asText(),
                    agent.get("roleType").asText(),
                    extractModelName(agent.get("llmConfig"))
            );
        }

        writer.println(table.build());
        writer.flush();
    }

    private void handleCreate(CommandContext ctx) {
        PrintWriter writer = ctx.writer();
        writer.println("=== 에이전트 생성 ===");
        writer.flush();

        String name = formReader.readLine(ctx, "이름");
        if (name == null) {
            printCancelled(writer);
            return;
        }

        int roleIndex = formReader.readSelection(ctx, "역할 유형 선택", ROLE_TYPE_OPTIONS);
        if (roleIndex < 0) {
            printCancelled(writer);
            return;
        }
        String roleType = ROLE_TYPE_VALUES.get(roleIndex);

        String role = formReader.readMultiLine(ctx, "시스템 프롬프트");
        if (role == null || role.isEmpty()) {
            printCancelled(writer);
            return;
        }

        String provider = formReader.readLine(ctx, "LLM Provider", "claude");
        if (provider == null) {
            printCancelled(writer);
            return;
        }

        String model = formReader.readLine(ctx, "LLM 모델", "claude-sonnet-4-20250514");
        if (model == null) {
            printCancelled(writer);
            return;
        }

        String apiKey = formReader.readSecret(ctx, "API Key (ref:secret/ 형식)");
        if (apiKey == null) {
            printCancelled(writer);
            return;
        }

        printCreateSummary(writer, name, roleType, role, provider, model);

        if (!formReader.readConfirm(ctx, "생성하시겠습니까?")) {
            printCancelled(writer);
            return;
        }

        Map<String, Object> llmConfig = new LinkedHashMap<>();
        llmConfig.put("provider", provider);
        llmConfig.put("model", model);
        llmConfig.put("apiKey", apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("roleType", roleType);
        body.put("role", role);
        body.put("llmConfig", llmConfig);

        Optional<JsonNode> response = apiClient.post(API_PATH, body);
        if (response.isPresent()) {
            JsonNode data = response.get().get("data");
            String id = data != null ? data.get("id").asText() : "?";
            writer.println("에이전트가 생성되었습니다. (ID: " + id + ")");
        } else {
            writer.println("에이전트 생성에 실패했습니다. 서버 연결을 확인해주세요.");
        }
        writer.flush();
    }

    private void handleUpdate(CommandContext ctx, String idArg) {
        PrintWriter writer = ctx.writer();

        if (idArg.isBlank()) {
            writer.println("사용법: /agent update {id}");
            writer.flush();
            return;
        }

        String id = idArg.trim();
        Optional<JsonNode> existing = apiClient.get(API_PATH + "/" + id);
        if (existing.isEmpty()) {
            writer.println("에이전트를 찾을 수 없습니다. (ID: " + id + ")");
            writer.flush();
            return;
        }

        JsonNode agentData = existing.get().get("data");
        if (agentData == null) {
            writer.println("에이전트 데이터를 읽을 수 없습니다.");
            writer.flush();
            return;
        }

        writer.println("=== 에이전트 수정 (ID: " + id + ") ===");
        writer.println("(Enter로 기존 값 유지)");
        writer.flush();

        String currentName = extractField(agentData, "name");
        String currentRole = extractField(agentData, "role");
        JsonNode currentLlmConfig = agentData.get("llmConfig");

        String name = formReader.readLine(ctx, "이름", currentName);
        if (name == null) {
            printCancelled(writer);
            return;
        }

        writer.println("현재 시스템 프롬프트:");
        writer.println("  " + truncate(currentRole, 80));
        writer.flush();

        String role = formReader.readMultiLine(ctx, "새 시스템 프롬프트 (빈 줄만 입력하면 기존 유지)");
        if (role == null) {
            printCancelled(writer);
            return;
        }
        if (role.isEmpty()) {
            role = currentRole;
        }

        String currentProvider = extractField(currentLlmConfig, "provider");
        String currentModel = extractField(currentLlmConfig, "model");
        String currentApiKey = extractField(currentLlmConfig, "apiKey");

        String provider = formReader.readLine(ctx, "LLM Provider", currentProvider);
        if (provider == null) {
            printCancelled(writer);
            return;
        }

        String model = formReader.readLine(ctx, "LLM 모델", currentModel);
        if (model == null) {
            printCancelled(writer);
            return;
        }

        String apiKey = formReader.readSecret(ctx, "API Key", currentApiKey);
        if (apiKey == null) {
            printCancelled(writer);
            return;
        }

        if (!formReader.readConfirm(ctx, "수정하시겠습니까?")) {
            printCancelled(writer);
            return;
        }

        Map<String, Object> llmConfig = new LinkedHashMap<>();
        llmConfig.put("provider", provider);
        llmConfig.put("model", model);
        llmConfig.put("apiKey", apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("role", role);
        body.put("llmConfig", llmConfig);

        Optional<JsonNode> response = apiClient.put(API_PATH + "/" + id, body);
        if (response.isPresent()) {
            writer.println("에이전트가 수정되었습니다. (ID: " + id + ")");
        } else {
            writer.println("에이전트 수정에 실패했습니다.");
        }
        writer.flush();
    }

    private void handleDelete(CommandContext ctx, String idArg) {
        PrintWriter writer = ctx.writer();

        if (idArg.isBlank()) {
            writer.println("사용법: /agent delete {id}");
            writer.flush();
            return;
        }

        String id = idArg.trim();

        Optional<JsonNode> existing = apiClient.get(API_PATH + "/" + id);
        if (existing.isEmpty()) {
            writer.println("에이전트를 찾을 수 없습니다. (ID: " + id + ")");
            writer.flush();
            return;
        }

        JsonNode agentData = existing.get().get("data");
        String agentName = agentData != null ? agentData.get("name").asText() : id;

        if (!formReader.readConfirm(ctx, "'" + agentName + "' 에이전트를 삭제하시겠습니까?")) {
            printCancelled(writer);
            return;
        }

        boolean success = apiClient.delete(API_PATH + "/" + id);
        if (success) {
            writer.println("에이전트가 삭제되었습니다. (ID: " + id + ")");
        } else {
            writer.println("에이전트 삭제에 실패했습니다.");
        }
        writer.flush();
    }

    private void handleDetail(CommandContext ctx, String idArg) {
        PrintWriter writer = ctx.writer();

        try {
            Long.parseLong(idArg);
        } catch (NumberFormatException e) {
            writer.println("사용법: /agent [list|create|update|delete] 또는 /agent {id}");
            writer.flush();
            return;
        }

        Optional<JsonNode> response = apiClient.get(API_PATH + "/" + idArg);
        if (response.isEmpty()) {
            writer.println("에이전트를 찾을 수 없습니다. (ID: " + idArg + ")");
            writer.flush();
            return;
        }

        JsonNode data = response.get().get("data");
        if (data == null) {
            writer.println("에이전트 데이터를 읽을 수 없습니다.");
            writer.flush();
            return;
        }

        writer.println("=== 에이전트 상세 (ID: " + idArg + ") ===");
        writer.println("이름:           " + extractField(data, "name"));
        writer.println("역할 유형:      " + extractField(data, "roleType"));
        writer.println("시스템 프롬프트: " + extractField(data, "role"));
        writer.println("LLM 설정:");

        JsonNode llmConfig = data.get("llmConfig");
        if (llmConfig != null && llmConfig.isObject()) {
            llmConfig.fields().forEachRemaining(field ->
                    writer.println("  " + field.getKey() + ": " + maskSecretValue(field.getKey(), field.getValue().asText()))
            );
        }

        writer.println("생성일:         " + extractField(data, "createdAt"));
        writer.println("수정일:         " + extractField(data, "updatedAt"));
        writer.flush();
    }

    private void printCreateSummary(PrintWriter writer, String name, String roleType,
                                     String role, String provider, String model) {
        writer.println();
        writer.println("--- 입력 확인 ---");
        writer.println("이름:      " + name);
        writer.println("역할 유형: " + roleType);
        writer.println("프롬프트:  " + truncate(role, 60));
        writer.println("LLM:       " + provider + " / " + model);
        writer.flush();
    }

    private void printCancelled(PrintWriter writer) {
        writer.println("취소되었습니다.");
        writer.flush();
    }

    private String extractModelName(JsonNode llmConfig) {
        if (llmConfig == null || !llmConfig.has("model")) {
            return "-";
        }
        return llmConfig.get("model").asText();
    }

    private String extractField(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName)) {
            return "";
        }
        return node.get(fieldName).asText();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String singleLine = text.replace('\n', ' ');
        if (singleLine.length() <= maxLen) {
            return singleLine;
        }
        return singleLine.substring(0, maxLen - 3) + "...";
    }

    private String maskSecretValue(String key, String value) {
        if ("apiKey".equalsIgnoreCase(key) && value != null && !value.startsWith("ref:secret/")) {
            if (value.length() <= 8) {
                return "****";
            }
            return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
        }
        return value;
    }
}
