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

/**
 * Secret 관리 슬래시 커맨드.
 *
 * <p>{@code /secret} 커맨드를 통해 Secret의 목록 조회, 생성, 수정, 삭제를 수행한다.
 * Secret 값은 AES-256으로 암호화되어 저장되며,
 * CLI에서도 값 입력 시 마스킹 처리된다.
 * 목록/상세 조회 시 값은 노출되지 않고 참조 형식({@code ref:secret/{name}})만 표시한다.</p>
 */
@Component
public class SecretCommand {

    private static final String API_PATH = "/api/v1/secrets";

    private final SquadApiClient apiClient;
    private final TableRenderer tableRenderer;
    private final InteractiveFormReader formReader;

    public SecretCommand(CommandRegistry commandRegistry,
                         SquadApiClient apiClient,
                         TableRenderer tableRenderer,
                         InteractiveFormReader formReader) {
        this.apiClient = apiClient;
        this.tableRenderer = tableRenderer;
        this.formReader = formReader;
        commandRegistry.register("secret", "Secret 관리 (list/create/update/delete)", this::execute,
                List.of(
                        new CommandRegistry.SubcommandInfo("list", "Secret 목록 조회"),
                        new CommandRegistry.SubcommandInfo("create", "새 Secret 생성"),
                        new CommandRegistry.SubcommandInfo("update", "Secret 수정"),
                        new CommandRegistry.SubcommandInfo("delete", "Secret 삭제")
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
            writer.println("등록된 Secret이 없습니다.");
            writer.flush();
            return;
        }

        TableRenderer.Builder table = tableRenderer.builder()
                .headers("ID", "이름", "참조 형식", "생성일");

        for (JsonNode secret : data) {
            String name = secret.get("name").asText();
            table.row(
                    secret.get("id").asText(),
                    name,
                    "ref:secret/" + name,
                    extractField(secret, "createdAt")
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
            writer.println("사용법: /secret [list|create|update|delete] 또는 /secret {id}");
            writer.flush();
            return;
        }

        Optional<JsonNode> response = apiClient.get(API_PATH + "/" + idArg);
        if (response.isEmpty()) {
            writer.println("Secret을 찾을 수 없습니다. (ID: " + idArg + ")");
            writer.flush();
            return;
        }

        JsonNode data = response.get().get("data");
        if (data == null) {
            writer.println("Secret 데이터를 읽을 수 없습니다.");
            writer.flush();
            return;
        }

        String name = extractField(data, "name");
        writer.println("=== Secret 상세 (ID: " + idArg + ") ===");
        writer.println("이름:     " + name);
        writer.println("참조:     ref:secret/" + name);
        writer.println("값:       ********");
        writer.println("생성일:   " + extractField(data, "createdAt"));
        writer.println("수정일:   " + extractField(data, "updatedAt"));
        writer.flush();
    }

    private void handleCreate(CommandContext ctx) {
        PrintWriter writer = ctx.writer();
        writer.println("=== Secret 생성 ===");
        writer.flush();

        String name = formReader.readLine(ctx, "이름");
        if (name == null) {
            printCancelled(writer);
            return;
        }

        String value = formReader.readSecret(ctx, "값");
        if (value == null) {
            printCancelled(writer);
            return;
        }

        writer.println();
        writer.println("--- 입력 확인 ---");
        writer.println("이름: " + name);
        writer.println("참조: ref:secret/" + name);
        writer.println("값:   ********");
        writer.flush();

        if (!formReader.readConfirm(ctx, "생성하시겠습니까?")) {
            printCancelled(writer);
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("value", value);

        Optional<JsonNode> response = apiClient.post(API_PATH, body);
        if (response.isPresent()) {
            JsonNode data = response.get().get("data");
            String id = data != null ? data.get("id").asText() : "?";
            writer.println("Secret이 생성되었습니다. (ID: " + id + ")");
        } else {
            writer.println("Secret 생성에 실패했습니다. 서버 연결을 확인해주세요.");
        }
        writer.flush();
    }

    private void handleUpdate(CommandContext ctx, String idArg) {
        PrintWriter writer = ctx.writer();

        String id;
        if (idArg.isBlank()) {
            id = selectSecretInteractively(ctx, "수정할 Secret 선택");
            if (id == null) {
                return;
            }
        } else {
            id = idArg.trim();
        }

        Optional<JsonNode> existing = apiClient.get(API_PATH + "/" + id);
        if (existing.isEmpty()) {
            writer.println("Secret을 찾을 수 없습니다. (ID: " + id + ")");
            writer.flush();
            return;
        }

        JsonNode secretData = existing.get().get("data");
        if (secretData == null) {
            writer.println("Secret 데이터를 읽을 수 없습니다.");
            writer.flush();
            return;
        }

        String secretName = extractField(secretData, "name");
        writer.println("=== Secret 수정 (ID: " + id + ") ===");
        writer.println("이름: " + secretName + " (수정 불가)");
        writer.flush();

        String value = formReader.readSecret(ctx, "새 값");
        if (value == null) {
            printCancelled(writer);
            return;
        }

        if (!formReader.readConfirm(ctx, "수정하시겠습니까?")) {
            printCancelled(writer);
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("value", value);

        Optional<JsonNode> response = apiClient.put(API_PATH + "/" + id, body);
        if (response.isPresent()) {
            writer.println("Secret이 수정되었습니다. (ID: " + id + ")");
        } else {
            writer.println("Secret 수정에 실패했습니다.");
        }
        writer.flush();
    }

    private void handleDelete(CommandContext ctx, String idArg) {
        PrintWriter writer = ctx.writer();

        String id;
        String secretName;
        if (idArg.isBlank()) {
            id = selectSecretInteractively(ctx, "삭제할 Secret 선택");
            if (id == null) {
                return;
            }
            Optional<JsonNode> existing = apiClient.get(API_PATH + "/" + id);
            JsonNode secretData = existing.map(r -> r.get("data")).orElse(null);
            secretName = secretData != null ? secretData.get("name").asText() : id;
        } else {
            id = idArg.trim();
            Optional<JsonNode> existing = apiClient.get(API_PATH + "/" + id);
            if (existing.isEmpty()) {
                writer.println("Secret을 찾을 수 없습니다. (ID: " + id + ")");
                writer.flush();
                return;
            }
            JsonNode secretData = existing.get().get("data");
            secretName = secretData != null ? secretData.get("name").asText() : id;
        }

        if (!formReader.readConfirm(ctx, "'" + secretName + "' Secret을 삭제하시겠습니까?")) {
            printCancelled(writer);
            return;
        }

        boolean success = apiClient.delete(API_PATH + "/" + id);
        if (success) {
            writer.println("Secret이 삭제되었습니다. (ID: " + id + ")");
        } else {
            writer.println("Secret 삭제에 실패했습니다.");
        }
        writer.flush();
    }

    /**
     * Secret 목록을 조회하여 화살표키 선택 UI로 하나를 고른다.
     *
     * @param ctx    커맨드 컨텍스트
     * @param prompt 선택 프롬프트
     * @return 선택된 Secret ID, 취소 또는 목록 없음 시 null
     */
    private String selectSecretInteractively(CommandContext ctx, String prompt) {
        PrintWriter writer = ctx.writer();
        Optional<JsonNode> response = apiClient.get(API_PATH);

        if (response.isEmpty()) {
            writer.println("서버에 연결할 수 없습니다.");
            writer.flush();
            return null;
        }

        JsonNode data = response.get().get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            writer.println("등록된 Secret이 없습니다.");
            writer.flush();
            return null;
        }

        List<String> options = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (JsonNode secret : data) {
            String secretId = secret.get("id").asText();
            String secretName = secret.get("name").asText();
            ids.add(secretId);
            options.add("[" + secretId + "] " + secretName);
        }

        int selected = formReader.readSelection(ctx, prompt, options);
        if (selected < 0) {
            printCancelled(writer);
            return null;
        }
        return ids.get(selected);
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
}
