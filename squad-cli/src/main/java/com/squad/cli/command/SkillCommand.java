package com.squad.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Skill 관리 슬래시 커맨드.
 *
 * <p>{@code /skill} 커맨드를 통해 Skill의 목록 조회, 생성, 수정, 삭제를 수행한다.
 * prompt 입력 시 에디터 또는 직접 입력을 지원하며,
 * requiredMcps 선택 시 멀티 선택 UI를 제공한다.</p>
 */
@Component
public class SkillCommand {

    private static final String API_PATH = "/api/v1/skills";
    private static final String MCPS_API_PATH = "/api/v1/mcps";

    private final SquadApiClient apiClient;
    private final TableRenderer tableRenderer;
    private final InteractiveFormReader formReader;

    public SkillCommand(CommandRegistry commandRegistry,
                        SquadApiClient apiClient,
                        TableRenderer tableRenderer,
                        InteractiveFormReader formReader) {
        this.apiClient = apiClient;
        this.tableRenderer = tableRenderer;
        this.formReader = formReader;
        commandRegistry.register("skill", "Skill 관리 (list/create/update/delete)", this::execute,
                List.of(
                        new CommandRegistry.SubcommandInfo("list", "Skill 목록 조회"),
                        new CommandRegistry.SubcommandInfo("create", "새 Skill 생성"),
                        new CommandRegistry.SubcommandInfo("update", "Skill 수정"),
                        new CommandRegistry.SubcommandInfo("delete", "Skill 삭제")
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
            writer.println("등록된 Skill이 없습니다.");
            writer.flush();
            return;
        }

        TableRenderer.Builder table = tableRenderer.builder()
                .headers("ID", "이름", "설명", "필요 MCP 수");

        for (JsonNode skill : data) {
            JsonNode requiredMcps = skill.get("requiredMcps");
            int mcpCount = (requiredMcps != null && requiredMcps.isArray()) ? requiredMcps.size() : 0;
            table.row(
                    skill.get("id").asText(),
                    skill.get("name").asText(),
                    extractField(skill, "description"),
                    String.valueOf(mcpCount)
            );
        }

        writer.println(table.build());
        writer.flush();
    }

    private void handleDetail(CommandContext ctx, String idArg) {
        PrintWriter writer = ctx.writer();

        try {
            Long.parseLong(idArg);
        } catch (NumberFormatException e) {
            writer.println("사용법: /skill [list|create|update|delete] 또는 /skill {id}");
            writer.flush();
            return;
        }

        Optional<JsonNode> response = apiClient.get(API_PATH + "/" + idArg);
        if (response.isEmpty()) {
            writer.println("Skill을 찾을 수 없습니다. (ID: " + idArg + ")");
            writer.flush();
            return;
        }

        JsonNode data = response.get().get("data");
        if (data == null) {
            writer.println("Skill 데이터를 읽을 수 없습니다.");
            writer.flush();
            return;
        }

        writer.println("=== Skill 상세 (ID: " + idArg + ") ===");
        writer.println("이름:         " + extractField(data, "name"));
        writer.println("설명:         " + extractField(data, "description"));
        writer.println("필요 MCP IDs: " + formatLongArray(data.get("requiredMcps")));
        writer.println("프롬프트:");
        printPrompt(writer, extractField(data, "prompt"));
        writer.println("생성일:       " + extractField(data, "createdAt"));
        writer.println("수정일:       " + extractField(data, "updatedAt"));
        writer.flush();
    }

    private void handleCreate(CommandContext ctx) {
        PrintWriter writer = ctx.writer();
        writer.println("=== Skill 생성 ===");
        writer.flush();

        String name = formReader.readLine(ctx, "이름");
        if (name == null) {
            printCancelled(writer);
            return;
        }

        String description = formReader.readLine(ctx, "설명 (선택)");

        String prompt = readPromptInput(ctx, null);
        if (prompt == null) {
            printCancelled(writer);
            return;
        }

        List<Long> requiredMcps = readRequiredMcps(ctx, writer, null);
        if (requiredMcps == null) {
            requiredMcps = List.of();
        }

        printCreateSummary(writer, name, description, prompt, requiredMcps);

        if (!formReader.readConfirm(ctx, "생성하시겠습니까?")) {
            printCancelled(writer);
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", description);
        body.put("prompt", prompt);
        if (requiredMcps != null && !requiredMcps.isEmpty()) {
            body.put("requiredMcps", requiredMcps);
        }

        Optional<JsonNode> response = apiClient.post(API_PATH, body);
        if (response.isPresent()) {
            JsonNode data = response.get().get("data");
            String id = data != null ? data.get("id").asText() : "?";
            writer.println("Skill이 생성되었습니다. (ID: " + id + ")");
        } else {
            writer.println("Skill 생성에 실패했습니다. 서버 연결을 확인해주세요.");
        }
        writer.flush();
    }

    private void handleUpdate(CommandContext ctx, String idArg) {
        PrintWriter writer = ctx.writer();

        String id;
        if (idArg.isBlank()) {
            id = selectSkillInteractively(ctx, "수정할 Skill 선택");
            if (id == null) {
                return;
            }
        } else {
            id = idArg.trim();
        }

        Optional<JsonNode> existing = apiClient.get(API_PATH + "/" + id);
        if (existing.isEmpty()) {
            writer.println("Skill을 찾을 수 없습니다. (ID: " + id + ")");
            writer.flush();
            return;
        }

        JsonNode skillData = existing.get().get("data");
        if (skillData == null) {
            writer.println("Skill 데이터를 읽을 수 없습니다.");
            writer.flush();
            return;
        }

        writer.println("=== Skill 수정 (ID: " + id + ") ===");
        writer.println("(Enter로 기존 값 유지)");
        writer.flush();

        String currentName = extractField(skillData, "name");
        String currentDescription = extractField(skillData, "description");
        String currentPrompt = extractField(skillData, "prompt");

        String name = formReader.readLine(ctx, "이름", currentName);
        if (name == null) {
            printCancelled(writer);
            return;
        }

        String description = formReader.readLine(ctx, "설명",
                currentDescription.isEmpty() ? null : currentDescription);

        // 프롬프트 수정
        writer.println("현재 프롬프트:");
        printPrompt(writer, currentPrompt);
        writer.flush();

        String prompt = currentPrompt;
        if (formReader.readConfirm(ctx, "프롬프트를 변경하시겠습니까?")) {
            String newPrompt = readPromptInput(ctx, currentPrompt);
            if (newPrompt == null) {
                printCancelled(writer);
                return;
            }
            prompt = newPrompt;
        }

        // requiredMcps 수정
        JsonNode currentMcpsNode = skillData.get("requiredMcps");
        List<Long> requiredMcps = parseLongArray(currentMcpsNode);

        if (!requiredMcps.isEmpty()) {
            writer.println("현재 필요 MCP IDs: " + formatLongArray(currentMcpsNode));
            writer.flush();
        }

        if (!requiredMcps.isEmpty() || formReader.readConfirm(ctx, "필요 MCP를 설정하시겠습니까?")) {
            if (!requiredMcps.isEmpty() && !formReader.readConfirm(ctx, "필요 MCP를 변경하시겠습니까?")) {
                // 기존값 유지
            } else {
                List<Long> newMcps = readRequiredMcps(ctx, writer, requiredMcps);
                if (newMcps != null) {
                    requiredMcps = newMcps;
                }
            }
        }

        if (!formReader.readConfirm(ctx, "수정하시겠습니까?")) {
            printCancelled(writer);
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("description", description);
        body.put("prompt", prompt);
        body.put("requiredMcps", requiredMcps);

        Optional<JsonNode> response = apiClient.put(API_PATH + "/" + id, body);
        if (response.isPresent()) {
            writer.println("Skill이 수정되었습니다. (ID: " + id + ")");
        } else {
            writer.println("Skill 수정에 실패했습니다.");
        }
        writer.flush();
    }

    private void handleDelete(CommandContext ctx, String idArg) {
        PrintWriter writer = ctx.writer();

        String id;
        String skillName;
        if (idArg.isBlank()) {
            id = selectSkillInteractively(ctx, "삭제할 Skill 선택");
            if (id == null) {
                return;
            }
            Optional<JsonNode> existing = apiClient.get(API_PATH + "/" + id);
            JsonNode skillData = existing.map(r -> r.get("data")).orElse(null);
            skillName = skillData != null ? skillData.get("name").asText() : id;
        } else {
            id = idArg.trim();
            Optional<JsonNode> existing = apiClient.get(API_PATH + "/" + id);
            if (existing.isEmpty()) {
                writer.println("Skill을 찾을 수 없습니다. (ID: " + id + ")");
                writer.flush();
                return;
            }
            JsonNode skillData = existing.get().get("data");
            skillName = skillData != null ? skillData.get("name").asText() : id;
        }

        if (!formReader.readConfirm(ctx, "'" + skillName + "' Skill을 삭제하시겠습니까?")) {
            printCancelled(writer);
            return;
        }

        boolean success = apiClient.delete(API_PATH + "/" + id);
        if (success) {
            writer.println("Skill이 삭제되었습니다. (ID: " + id + ")");
        } else {
            writer.println("Skill 삭제에 실패했습니다.");
        }
        writer.flush();
    }

    /**
     * 프롬프트 텍스트를 에디터 또는 직접 입력으로 받는다.
     *
     * @param ctx           커맨드 컨텍스트
     * @param existingValue 기존 프롬프트 (update 시, null 가능)
     * @return 프롬프트 텍스트, 취소 시 null
     */
    private String readPromptInput(CommandContext ctx, String existingValue) {
        String editorName = formReader.resolveEditorName();
        List<String> options = List.of(
                "에디터로 편집 (" + editorName + ")",
                "직접 입력"
        );

        int selected = formReader.readSelection(ctx, "프롬프트 입력 방법", options);
        if (selected < 0) {
            return null;
        }

        if (selected == 0) {
            return formReader.readWithEditor(ctx, existingValue, ".txt");
        }

        String input = formReader.readMultiLine(ctx, "프롬프트");
        if (input == null || input.isEmpty()) {
            return existingValue;
        }
        return input;
    }

    /**
     * 등록된 MCP 목록에서 필요 MCP를 멀티 선택 UI로 선택받는다.
     *
     * @param ctx           커맨드 컨텍스트
     * @param writer        출력용 PrintWriter
     * @param preSelectedIds 미리 선택할 MCP ID 목록 (null 가능)
     * @return 선택된 MCP ID 리스트, 선택 없음 시 빈 리스트, 취소 시 null
     */
    private List<Long> readRequiredMcps(CommandContext ctx, PrintWriter writer,
                                        List<Long> preSelectedIds) {
        Optional<JsonNode> response = apiClient.get(MCPS_API_PATH);
        if (response.isEmpty()) {
            writer.println("MCP 서버에 연결할 수 없습니다. 기존 MCP 설정을 유지합니다.");
            writer.flush();
            return null;
        }

        JsonNode data = response.get().get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            writer.println("등록된 MCP가 없습니다.");
            writer.flush();
            return List.of();
        }

        List<String> options = new ArrayList<>();
        List<Long> mcpIdList = new ArrayList<>();
        List<Integer> preSelected = new ArrayList<>();

        for (JsonNode mcp : data) {
            long mcpId = mcp.get("id").asLong();
            String mcpName = mcp.get("name").asText();
            mcpIdList.add(mcpId);
            options.add("[" + mcpId + "] " + mcpName);

            if (preSelectedIds != null && preSelectedIds.contains(mcpId)) {
                preSelected.add(options.size() - 1);
            }
        }

        List<Integer> selectedIndices = formReader.readMultiSelection(
                ctx, "필요 MCP 선택 (선택사항)", options,
                preSelected.isEmpty() ? null : preSelected);
        if (selectedIndices == null) {
            return null;
        }

        return selectedIndices.stream()
                .map(mcpIdList::get)
                .collect(Collectors.toList());
    }

    /**
     * Skill 목록을 조회하여 화살표키 선택 UI로 하나를 고른다.
     *
     * @param ctx    커맨드 컨텍스트
     * @param prompt 선택 프롬프트
     * @return 선택된 Skill ID, 취소 또는 목록 없음 시 null
     */
    private String selectSkillInteractively(CommandContext ctx, String prompt) {
        PrintWriter writer = ctx.writer();
        Optional<JsonNode> response = apiClient.get(API_PATH);

        if (response.isEmpty()) {
            writer.println("서버에 연결할 수 없습니다.");
            writer.flush();
            return null;
        }

        JsonNode data = response.get().get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            writer.println("등록된 Skill이 없습니다.");
            writer.flush();
            return null;
        }

        List<String> options = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (JsonNode skill : data) {
            String skillId = skill.get("id").asText();
            String skillName = skill.get("name").asText();
            String desc = extractField(skill, "description");
            ids.add(skillId);
            String label = "[" + skillId + "] " + skillName;
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
                                     String prompt, List<Long> requiredMcps) {
        writer.println();
        writer.println("--- 입력 확인 ---");
        writer.println("이름:         " + name);
        writer.println("설명:         " + (description != null ? description : "(없음)"));
        writer.println("프롬프트:     " + truncate(prompt, 60));
        writer.println("필요 MCP IDs: "
                + (requiredMcps != null && !requiredMcps.isEmpty() ? requiredMcps : "(없음)"));
        writer.flush();
    }

    private void printPrompt(PrintWriter writer, String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            writer.println("  (없음)");
            return;
        }
        for (String line : prompt.split("\n")) {
            writer.println("  " + line);
        }
    }

    private void printCancelled(PrintWriter writer) {
        writer.println("취소되었습니다.");
        writer.flush();
    }

    private String extractField(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return "";
        }
        return node.get(fieldName).asText();
    }

    private List<Long> parseLongArray(JsonNode arrayNode) {
        List<Long> result = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode idNode : arrayNode) {
                result.add(idNode.asLong());
            }
        }
        return result;
    }

    private String formatLongArray(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray() || arrayNode.isEmpty()) {
            return "(없음)";
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode idNode : arrayNode) {
            ids.add(idNode.asText());
        }
        return String.join(", ", ids);
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
}
