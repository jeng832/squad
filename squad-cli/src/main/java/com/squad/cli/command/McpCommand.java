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

/**
 * MCP 관리 슬래시 커맨드.
 *
 * <p>{@code /mcp} 커맨드를 통해 MCP의 목록 조회, 생성, 수정, 삭제를 수행한다.
 * config JSON 입력 시 직접 입력 또는 {@code @파일경로}를 통한 파일 읽기를 지원한다.</p>
 */
@Component
public class McpCommand {

    private static final String API_PATH = "/api/v1/mcps";

    private final SquadApiClient apiClient;
    private final TableRenderer tableRenderer;
    private final InteractiveFormReader formReader;
    private final ObjectMapper objectMapper;

    public McpCommand(CommandRegistry commandRegistry,
                      SquadApiClient apiClient,
                      TableRenderer tableRenderer,
                      InteractiveFormReader formReader) {
        this.apiClient = apiClient;
        this.tableRenderer = tableRenderer;
        this.formReader = formReader;
        this.objectMapper = new ObjectMapper();
        commandRegistry.register("mcp", "MCP 관리 (list/create/update/delete)", this::execute,
                List.of(
                        new CommandRegistry.SubcommandInfo("list", "MCP 목록 조회"),
                        new CommandRegistry.SubcommandInfo("create", "새 MCP 생성"),
                        new CommandRegistry.SubcommandInfo("update", "MCP 수정"),
                        new CommandRegistry.SubcommandInfo("delete", "MCP 삭제")
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
            writer.println("등록된 MCP가 없습니다.");
            writer.flush();
            return;
        }

        TableRenderer.Builder table = tableRenderer.builder()
                .headers("ID", "이름", "설명", "커맨드");

        for (JsonNode mcp : data) {
            table.row(
                    mcp.get("id").asText(),
                    mcp.get("name").asText(),
                    extractField(mcp, "description"),
                    extractCommand(mcp.get("config"))
            );
        }

        writer.println(table.build());
        writer.flush();
    }

    private void handleCreate(CommandContext ctx) {
        PrintWriter writer = ctx.writer();
        writer.println("=== MCP 생성 ===");
        writer.flush();

        String name = formReader.readLine(ctx, "이름");
        if (name == null) {
            printCancelled(writer);
            return;
        }

        String description = readOptionalLine(ctx, "설명 (선택)");

        Map<String, Object> config = readConfigJson(ctx);
        if (config == null) {
            return;
        }

        printCreateSummary(writer, name, description, config);

        if (!formReader.readConfirm(ctx, "생성하시겠습니까?")) {
            printCancelled(writer);
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", description);
        body.put("config", config);

        Optional<JsonNode> response = apiClient.post(API_PATH, body);
        if (response.isPresent()) {
            JsonNode data = response.get().get("data");
            String id = data != null ? data.get("id").asText() : "?";
            writer.println("MCP가 생성되었습니다. (ID: " + id + ")");
        } else {
            writer.println("MCP 생성에 실패했습니다. 서버 연결을 확인해주세요.");
        }
        writer.flush();
    }

    private void handleUpdate(CommandContext ctx, String idArg) {
        PrintWriter writer = ctx.writer();

        String id;
        if (idArg.isBlank()) {
            id = selectMcpInteractively(ctx, "수정할 MCP 선택");
            if (id == null) {
                return;
            }
        } else {
            id = idArg.trim();
        }

        Optional<JsonNode> existing = apiClient.get(API_PATH + "/" + id);
        if (existing.isEmpty()) {
            writer.println("MCP를 찾을 수 없습니다. (ID: " + id + ")");
            writer.flush();
            return;
        }

        JsonNode mcpData = existing.get().get("data");
        if (mcpData == null) {
            writer.println("MCP 데이터를 읽을 수 없습니다.");
            writer.flush();
            return;
        }

        writer.println("=== MCP 수정 (ID: " + id + ") ===");
        writer.println("(Enter로 기존 값 유지)");
        writer.flush();

        String currentName = extractField(mcpData, "name");
        String currentDescription = extractField(mcpData, "description");
        JsonNode currentConfig = mcpData.get("config");

        String name = formReader.readLine(ctx, "이름", currentName);
        if (name == null) {
            printCancelled(writer);
            return;
        }

        String description = readOptionalLine(ctx, "설명", currentDescription);

        writer.println("현재 config:");
        printConfigPretty(writer, currentConfig);
        writer.flush();

        String configInput = formReader.readMultiLine(ctx, "새 config JSON (빈 줄만 입력하면 기존 유지)");
        if (configInput == null) {
            printCancelled(writer);
            return;
        }

        Map<String, Object> config;
        if (configInput.isEmpty()) {
            config = objectMapper.convertValue(currentConfig, Map.class);
        } else {
            config = parseConfigJson(writer, configInput);
            if (config == null) {
                return;
            }
        }

        if (!formReader.readConfirm(ctx, "수정하시겠습니까?")) {
            printCancelled(writer);
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", description);
        body.put("config", config);

        Optional<JsonNode> response = apiClient.put(API_PATH + "/" + id, body);
        if (response.isPresent()) {
            writer.println("MCP가 수정되었습니다. (ID: " + id + ")");
        } else {
            writer.println("MCP 수정에 실패했습니다.");
        }
        writer.flush();
    }

    private void handleDelete(CommandContext ctx, String idArg) {
        PrintWriter writer = ctx.writer();

        String id;
        String mcpName;
        if (idArg.isBlank()) {
            id = selectMcpInteractively(ctx, "삭제할 MCP 선택");
            if (id == null) {
                return;
            }
            Optional<JsonNode> existing = apiClient.get(API_PATH + "/" + id);
            JsonNode mcpData = existing.map(r -> r.get("data")).orElse(null);
            mcpName = mcpData != null ? mcpData.get("name").asText() : id;
        } else {
            id = idArg.trim();
            Optional<JsonNode> existing = apiClient.get(API_PATH + "/" + id);
            if (existing.isEmpty()) {
                writer.println("MCP를 찾을 수 없습니다. (ID: " + id + ")");
                writer.flush();
                return;
            }
            JsonNode mcpData = existing.get().get("data");
            mcpName = mcpData != null ? mcpData.get("name").asText() : id;
        }

        if (!formReader.readConfirm(ctx, "'" + mcpName + "' MCP를 삭제하시겠습니까?")) {
            printCancelled(writer);
            return;
        }

        boolean success = apiClient.delete(API_PATH + "/" + id);
        if (success) {
            writer.println("MCP가 삭제되었습니다. (ID: " + id + ")");
        } else {
            writer.println("MCP 삭제에 실패했습니다.");
        }
        writer.flush();
    }

    private void handleDetail(CommandContext ctx, String idArg) {
        PrintWriter writer = ctx.writer();

        try {
            Long.parseLong(idArg);
        } catch (NumberFormatException e) {
            writer.println("사용법: /mcp [list|create|update|delete] 또는 /mcp {id}");
            writer.flush();
            return;
        }

        Optional<JsonNode> response = apiClient.get(API_PATH + "/" + idArg);
        if (response.isEmpty()) {
            writer.println("MCP를 찾을 수 없습니다. (ID: " + idArg + ")");
            writer.flush();
            return;
        }

        JsonNode data = response.get().get("data");
        if (data == null) {
            writer.println("MCP 데이터를 읽을 수 없습니다.");
            writer.flush();
            return;
        }

        writer.println("=== MCP 상세 (ID: " + idArg + ") ===");
        writer.println("이름:   " + extractField(data, "name"));
        writer.println("설명:   " + extractField(data, "description"));
        writer.println("config:");
        printConfigPretty(writer, data.get("config"));
        writer.println("생성일: " + extractField(data, "createdAt"));
        writer.println("수정일: " + extractField(data, "updatedAt"));
        writer.flush();
    }

    /**
     * MCP 목록을 조회하여 화살표키 선택 UI로 하나를 고른다.
     *
     * @param ctx    커맨드 컨텍스트
     * @param prompt 선택 프롬프트
     * @return 선택된 MCP ID, 취소 또는 목록 없음 시 null
     */
    private String selectMcpInteractively(CommandContext ctx, String prompt) {
        PrintWriter writer = ctx.writer();
        Optional<JsonNode> response = apiClient.get(API_PATH);

        if (response.isEmpty()) {
            writer.println("서버에 연결할 수 없습니다.");
            writer.flush();
            return null;
        }

        JsonNode data = response.get().get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            writer.println("등록된 MCP가 없습니다.");
            writer.flush();
            return null;
        }

        List<String> options = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (JsonNode mcp : data) {
            String mcpId = mcp.get("id").asText();
            String name = mcp.get("name").asText();
            String desc = extractField(mcp, "description");
            ids.add(mcpId);
            String label = "[" + mcpId + "] " + name;
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

    /**
     * config JSON을 대화형으로 입력받는다.
     *
     * <p>파싱 실패 시 에러를 출력하고 재입력을 유도한다.
     * 최대 3회까지 시도하며, 초과하면 취소로 처리한다.</p>
     *
     * @param ctx 커맨드 컨텍스트
     * @return 파싱된 config Map, 취소 시 null
     */
    private Map<String, Object> readConfigJson(CommandContext ctx) {
        PrintWriter writer = ctx.writer();
        int maxAttempts = 3;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String input = formReader.readMultiLine(ctx, "config JSON");
            if (input == null) {
                printCancelled(writer);
                return null;
            }
            if (input.isEmpty()) {
                writer.println("config는 필수입니다.");
                writer.flush();
                continue;
            }

            Map<String, Object> config = parseConfigJson(writer, input);
            if (config != null) {
                return config;
            }
        }

        writer.println("config 입력 시도 횟수를 초과했습니다.");
        writer.flush();
        return null;
    }

    /**
     * JSON 문자열을 파싱하여 config Map으로 변환한다.
     *
     * <p>JSON 객체여야 하며, {@code command} 키가 필수이고 빈 문자열이 아니어야 한다.</p>
     *
     * @param writer 에러 출력용 PrintWriter
     * @param input  JSON 문자열
     * @return 파싱된 config Map, 실패 시 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfigJson(PrintWriter writer, String input) {
        try {
            JsonNode node = objectMapper.readTree(input);
            if (!node.isObject()) {
                writer.println("config는 JSON 객체여야 합니다.");
                writer.flush();
                return null;
            }

            if (!node.has("command") || !node.get("command").isTextual()
                    || node.get("command").asText().isBlank()) {
                writer.println("config에 'command' 키가 필요합니다. (비어있지 않은 문자열)");
                writer.flush();
                return null;
            }

            return objectMapper.convertValue(node, Map.class);
        } catch (JsonProcessingException e) {
            writer.println("JSON 파싱 실패: " + e.getOriginalMessage());
            writer.flush();
            return null;
        }
    }

    private void printCreateSummary(PrintWriter writer, String name, String description,
                                     Map<String, Object> config) {
        writer.println();
        writer.println("--- 입력 확인 ---");
        writer.println("이름:   " + name);
        writer.println("설명:   " + (description != null ? description : "(없음)"));
        writer.println("config: " + formatConfigSummary(config));
        writer.flush();
    }

    /**
     * 선택사항 필드를 입력받는다. 빈 입력 시 null을 반환한다.
     *
     * <p>{@code readLine}은 빈 입력과 취소를 모두 null로 반환하므로,
     * 선택사항 필드에서는 null을 "입력 없음"으로 취급한다.</p>
     *
     * @param ctx    커맨드 컨텍스트
     * @param prompt 입력 프롬프트
     * @return 사용자 입력 문자열, 빈 입력 또는 취소 시 null
     */
    private String readOptionalLine(CommandContext ctx, String prompt) {
        return formReader.readLine(ctx, prompt);
    }

    /**
     * 선택사항 필드를 기본값과 함께 입력받는다.
     *
     * <p>기본값이 빈 문자열이면 null로 변환하여 전달하고,
     * 사용자가 Enter를 누르면 빈 문자열(기존 값 유지)을 반환한다.</p>
     *
     * @param ctx          커맨드 컨텍스트
     * @param prompt       입력 프롬프트
     * @param defaultValue 기본값
     * @return 사용자 입력 문자열, 빈 입력 시 기본값, 취소 시에도 기본값
     */
    private String readOptionalLine(CommandContext ctx, String prompt, String defaultValue) {
        String effectiveDefault = (defaultValue == null || defaultValue.isEmpty()) ? null : defaultValue;
        String result = formReader.readLine(ctx, prompt, effectiveDefault);
        return result;
    }

    private void printCancelled(PrintWriter writer) {
        writer.println("취소되었습니다.");
        writer.flush();
    }

    private void printConfigPretty(PrintWriter writer, JsonNode config) {
        if (config == null || config.isNull()) {
            writer.println("  (없음)");
            return;
        }
        try {
            String pretty = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(config);
            for (String line : pretty.split("\n")) {
                writer.println("  " + line);
            }
        } catch (JsonProcessingException e) {
            writer.println("  " + config);
        }
    }

    private String extractField(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return "";
        }
        return node.get(fieldName).asText();
    }

    private String extractCommand(JsonNode config) {
        if (config == null || !config.has("command")) {
            return "-";
        }
        return config.get("command").asText();
    }

    private String formatConfigSummary(Map<String, Object> config) {
        Object command = config.get("command");
        Object args = config.get("args");
        if (args != null) {
            return command + " " + args;
        }
        return String.valueOf(command);
    }
}
