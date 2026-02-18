package com.squad.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.squad.cli.api.SquadApiClient;
import com.squad.cli.config.CliConfig;
import com.squad.cli.form.InteractiveFormReader;
import com.squad.cli.shell.CommandContext;
import com.squad.cli.shell.CommandRegistry;
import com.squad.cli.ui.TableRenderer;
import com.squad.cli.websocket.SessionMonitorHandler;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 세션 관리 슬래시 커맨드.
 *
 * <p>{@code /session} 커맨드를 통해 세션의 목록 조회, 시작, 취소, 상태 확인, 결과 조회,
 * 실시간 모니터링을 수행한다. 세션 시작 시 Squad 선택과 프롬프트 입력을 가이드 폼으로 안내한다.</p>
 *
 * <p>{@code /session monitor {id}}로 WebSocket(STOMP) 연결을 통해 세션의 진행 상황을
 * 실시간으로 모니터링할 수 있다.</p>
 */
@Component
public class SessionCommand {

    private static final String API_PATH = "/api/v1/sessions";
    private static final String SQUADS_API_PATH = "/api/v1/squads";
    private static final long WEBSOCKET_CONNECT_TIMEOUT_SECONDS = 10;

    private final SquadApiClient apiClient;
    private final TableRenderer tableRenderer;
    private final InteractiveFormReader formReader;
    private final WebSocketStompClient stompClient;
    private final CliConfig cliConfig;

    public SessionCommand(CommandRegistry commandRegistry,
                          SquadApiClient apiClient,
                          TableRenderer tableRenderer,
                          InteractiveFormReader formReader,
                          WebSocketStompClient stompClient,
                          CliConfig cliConfig) {
        this.apiClient = apiClient;
        this.tableRenderer = tableRenderer;
        this.formReader = formReader;
        this.stompClient = stompClient;
        this.cliConfig = cliConfig;
        commandRegistry.register("session", "세션 관리 (list/start/cancel/status/result/monitor)", this::execute,
                List.of(
                        new CommandRegistry.SubcommandInfo("list", "세션 목록 조회"),
                        new CommandRegistry.SubcommandInfo("start", "새 세션 시작"),
                        new CommandRegistry.SubcommandInfo("cancel", "세션 취소"),
                        new CommandRegistry.SubcommandInfo("status", "세션 상태 확인"),
                        new CommandRegistry.SubcommandInfo("result", "세션 결과 조회"),
                        new CommandRegistry.SubcommandInfo("monitor", "세션 실시간 모니터링")
                ));
    }

    private void execute(CommandContext ctx, String args) {
        String[] parts = args.trim().split("\\s+", 2);
        String subcommand = parts[0];
        String subArgs = parts.length > 1 ? parts[1] : "";

        switch (subcommand) {
            case "list", "" -> handleList(ctx);
            case "start" -> handleStart(ctx);
            case "cancel" -> handleCancel(ctx, subArgs);
            case "status" -> handleStatus(ctx, subArgs);
            case "result" -> handleResult(ctx, subArgs);
            case "monitor" -> handleMonitor(ctx, subArgs);
            default -> handleStatus(ctx, subcommand);
        }
    }

    /**
     * 세션 목록을 테이블로 표시한다.
     *
     * @param ctx 커맨드 컨텍스트
     */
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
            writer.println("등록된 세션이 없습니다.");
            writer.flush();
            return;
        }

        TableRenderer.Builder table = tableRenderer.builder()
                .headers("ID", "Squad ID", "상태", "프롬프트", "생성일");

        for (JsonNode session : data) {
            table.row(
                    session.get("id").asText(),
                    extractField(session, "squadId"),
                    extractField(session, "status"),
                    truncate(extractField(session, "userPrompt"), 40),
                    formatDateTime(extractField(session, "createdAt"))
            );
        }

        writer.println(table.build());
        writer.flush();
    }

    /**
     * 새 세션을 시작한다.
     *
     * <p>Squad 선택 → 프롬프트 입력 → 확인 → 생성(POST) → 시작(POST) 순으로 진행한다.</p>
     *
     * @param ctx 커맨드 컨텍스트
     */
    private void handleStart(CommandContext ctx) {
        PrintWriter writer = ctx.writer();
        writer.println("=== 세션 시작 ===");
        writer.flush();

        String squadId = selectSquadInteractively(ctx, "Squad 선택");
        if (squadId == null) {
            return;
        }

        String userPrompt = formReader.readMultiLine(ctx, "프롬프트 입력");
        if (userPrompt == null) {
            printCancelled(writer);
            return;
        }
        if (userPrompt.isBlank()) {
            writer.println("프롬프트를 입력해주세요.");
            writer.flush();
            return;
        }

        String repoUrl = formReader.readLine(ctx, "Git 저장소 URL (선택, Enter로 건너뛰기)");
        String branch = null;
        String gitProvider = null;
        String gitSecretName = null;

        if (repoUrl != null && !repoUrl.isBlank()) {
            branch = formReader.readLine(ctx, "브랜치", "main");

            gitProvider = detectGitProvider(repoUrl);
            if (gitProvider != null) {
                writer.println("  → Provider 자동 감지: " + gitProvider);
                writer.flush();
            } else {
                List<String> providerOptions = List.of("GITHUB", "GITLAB");
                int providerIndex = formReader.readSelection(ctx, "Git Provider 선택", providerOptions);
                if (providerIndex >= 0 && providerIndex < providerOptions.size()) {
                    gitProvider = providerOptions.get(providerIndex);
                }
            }

            gitSecretName = readGitSecret(ctx);
        }

        writer.println();
        writer.println("--- 입력 확인 ---");
        writer.println("Squad ID: " + squadId);
        writer.println("프롬프트: " + truncate(userPrompt, 60));
        if (repoUrl != null && !repoUrl.isBlank()) {
            writer.println("Git URL:  " + repoUrl);
            writer.println("브랜치:   " + (branch != null ? branch : "main"));
            writer.println("Provider: " + (gitProvider != null ? gitProvider : "(없음)"));
            writer.println("Secret:   " + (gitSecretName != null && !gitSecretName.isBlank() ? gitSecretName : "(없음)"));
        }
        writer.flush();

        if (!formReader.readConfirm(ctx, "세션을 시작하시겠습니까?")) {
            printCancelled(writer);
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("squadId", Long.parseLong(squadId));
        body.put("userPrompt", userPrompt);
        if (repoUrl != null && !repoUrl.isBlank()) {
            body.put("repoUrl", repoUrl);
            body.put("branch", branch);
            body.put("gitProvider", gitProvider);
            if (gitSecretName != null && !gitSecretName.isBlank()) {
                body.put("gitSecretName", gitSecretName);
            }
        }

        Optional<JsonNode> createResponse = apiClient.post(API_PATH, body);
        if (createResponse.isEmpty()) {
            writer.println("세션 생성에 실패했습니다. 서버 연결을 확인해주세요.");
            writer.flush();
            return;
        }

        JsonNode createData = createResponse.get().get("data");
        if (createData == null) {
            writer.println("세션 생성 응답을 읽을 수 없습니다.");
            writer.flush();
            return;
        }

        JsonNode idNode = createData.get("id");
        if (idNode == null || idNode.isNull()) {
            writer.println("세션 생성 응답에서 ID를 읽을 수 없습니다.");
            writer.flush();
            return;
        }
        String sessionId = idNode.asText();
        writer.println("세션이 생성되었습니다. (ID: " + sessionId + ")");
        writer.flush();

        Optional<JsonNode> startResponse = apiClient.post(API_PATH + "/" + sessionId + "/start", Map.of());
        if (startResponse.isPresent()) {
            writer.println("세션이 시작되었습니다. (ID: " + sessionId + ")");
            writer.println("'/session status " + sessionId + "'로 상태를 확인할 수 있습니다.");
        } else {
            writer.println("세션 시작에 실패했습니다. '/session status " + sessionId + "'로 상태를 확인해주세요.");
        }
        writer.flush();
    }

    /**
     * 세션을 취소한다.
     *
     * @param ctx    커맨드 컨텍스트
     * @param idArg  세션 ID (빈 문자열이면 선택 UI 표시)
     */
    private void handleCancel(CommandContext ctx, String idArg) {
        PrintWriter writer = ctx.writer();

        String id;
        if (idArg.isBlank()) {
            id = selectSessionInteractively(ctx, "취소할 세션 선택");
            if (id == null) {
                return;
            }
        } else {
            id = parseSessionId(writer, idArg);
            if (id == null) {
                return;
            }
        }

        Optional<JsonNode> existing = apiClient.get(API_PATH + "/" + id);
        if (existing.isEmpty()) {
            writer.println("세션을 찾을 수 없습니다. (ID: " + id + ")");
            writer.flush();
            return;
        }

        JsonNode sessionData = existing.get().get("data");
        if (sessionData == null) {
            writer.println("세션 데이터를 읽을 수 없습니다.");
            writer.flush();
            return;
        }

        String status = extractField(sessionData, "status");
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            writer.println("이미 " + status + " 상태인 세션입니다.");
            writer.flush();
            return;
        }

        writer.println("세션 ID: " + id);
        writer.println("상태:    " + status);
        writer.println("프롬프트: " + truncate(extractField(sessionData, "userPrompt"), 60));
        writer.flush();

        if (!formReader.readConfirm(ctx, "세션을 취소하시겠습니까?")) {
            printCancelled(writer);
            return;
        }

        Optional<JsonNode> response = apiClient.post(API_PATH + "/" + id + "/cancel", Map.of());
        if (response.isPresent()) {
            writer.println("세션이 취소되었습니다. (ID: " + id + ")");
        } else {
            writer.println("세션 취소에 실패했습니다.");
        }
        writer.flush();
    }

    /**
     * 세션 상태를 상세히 표시한다.
     *
     * @param ctx    커맨드 컨텍스트
     * @param idArg  세션 ID (빈 문자열이면 선택 UI 표시)
     */
    private void handleStatus(CommandContext ctx, String idArg) {
        PrintWriter writer = ctx.writer();

        String id;
        if (idArg.isBlank()) {
            id = selectSessionInteractively(ctx, "상태를 확인할 세션 선택");
            if (id == null) {
                return;
            }
        } else {
            id = parseSessionId(writer, idArg);
            if (id == null) {
                return;
            }
        }

        Optional<JsonNode> response = apiClient.get(API_PATH + "/" + id);
        if (response.isEmpty()) {
            writer.println("세션을 찾을 수 없습니다. (ID: " + id + ")");
            writer.flush();
            return;
        }

        JsonNode data = response.get().get("data");
        if (data == null) {
            writer.println("세션 데이터를 읽을 수 없습니다.");
            writer.flush();
            return;
        }

        writer.println("=== 세션 상세 (ID: " + id + ") ===");
        writer.println("Squad ID: " + extractField(data, "squadId"));
        writer.println("상태:     " + extractField(data, "status"));
        writer.println("프롬프트:");
        printMultiLineField(writer, extractField(data, "userPrompt"));
        writer.println("시작일:   " + formatDateTime(extractField(data, "startedAt")));
        writer.println("완료일:   " + formatDateTime(extractField(data, "completedAt")));
        writer.println("생성일:   " + formatDateTime(extractField(data, "createdAt")));

        String result = extractField(data, "result");
        if (!result.isEmpty()) {
            writer.println("결과:");
            printMultiLineField(writer, result);
        }
        writer.flush();
    }

    /**
     * 세션 결과를 표시한다.
     *
     * @param ctx    커맨드 컨텍스트
     * @param idArg  세션 ID (빈 문자열이면 선택 UI 표시)
     */
    private void handleResult(CommandContext ctx, String idArg) {
        PrintWriter writer = ctx.writer();

        String id;
        if (idArg.isBlank()) {
            id = selectSessionInteractively(ctx, "결과를 조회할 세션 선택");
            if (id == null) {
                return;
            }
        } else {
            id = parseSessionId(writer, idArg);
            if (id == null) {
                return;
            }
        }

        Optional<JsonNode> response = apiClient.get(API_PATH + "/" + id);
        if (response.isEmpty()) {
            writer.println("세션을 찾을 수 없습니다. (ID: " + id + ")");
            writer.flush();
            return;
        }

        JsonNode data = response.get().get("data");
        if (data == null) {
            writer.println("세션 데이터를 읽을 수 없습니다.");
            writer.flush();
            return;
        }

        String status = extractField(data, "status");
        String result = extractField(data, "result");

        writer.println("=== 세션 결과 (ID: " + id + ") ===");
        writer.println("상태: " + status);

        if (result.isEmpty()) {
            if ("RUNNING".equals(status) || "PENDING".equals(status)) {
                writer.println("세션이 아직 완료되지 않았습니다.");
            } else {
                writer.println("결과가 없습니다.");
            }
        } else {
            writer.println();
            printMultiLineField(writer, result);
        }
        writer.flush();
    }

    /**
     * 세션을 실시간으로 모니터링한다.
     *
     * <p>WebSocket(STOMP) 연결을 통해 서버의 {@code /topic/sessions/{sessionId}}를 구독하고,
     * 에이전트 상태 변경, 메시지, 세션 완료 이벤트를 실시간으로 터미널에 출력한다.</p>
     *
     * <p>세션이 완료({@code SESSION_COMPLETE})되면 자동으로 종료된다.
     * Ctrl+C로 중간에 모니터링을 중단할 수 있다.</p>
     *
     * @param ctx    커맨드 컨텍스트
     * @param idArg  세션 ID (빈 문자열이면 선택 UI 표시)
     */
    private void handleMonitor(CommandContext ctx, String idArg) {
        PrintWriter writer = ctx.writer();

        String id;
        if (idArg.isBlank()) {
            id = selectSessionInteractively(ctx, "모니터링할 세션 선택");
            if (id == null) {
                return;
            }
        } else {
            id = parseSessionId(writer, idArg);
            if (id == null) {
                return;
            }
        }

        String wsUrl = buildWebSocketUrl();
        CountDownLatch latch = new CountDownLatch(1);
        SessionMonitorHandler handler = new SessionMonitorHandler(id, writer, latch);

        StompSession stompSession = null;
        try {
            CompletableFuture<StompSession> future = stompClient.connectAsync(wsUrl, handler);
            stompSession = future.get(WEBSOCKET_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writer.println("[중단] 모니터링이 중단되었습니다.");
        } catch (ExecutionException e) {
            writer.println("[오류] WebSocket 연결 실패: " + e.getCause().getMessage());
        } catch (TimeoutException e) {
            writer.println("[오류] WebSocket 연결 시간 초과");
        } finally {
            if (stompSession != null && stompSession.isConnected()) {
                stompSession.disconnect();
            }
            writer.flush();
        }
    }

    /**
     * 서버 URL에서 WebSocket URL을 생성한다.
     *
     * <p>{@code http://localhost:8080}을 {@code ws://localhost:8080/ws}로 변환한다.
     * {@code https}는 {@code wss}로 변환한다.</p>
     *
     * @return WebSocket URL
     */
    private String buildWebSocketUrl() {
        String serverUrl = cliConfig.getServerUrl();
        String wsUrl = serverUrl.replaceFirst("^https://", "wss://")
                .replaceFirst("^http://", "ws://");
        return wsUrl + "/ws";
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
        Optional<JsonNode> response = apiClient.get(SQUADS_API_PATH);

        if (response.isEmpty()) {
            writer.println("서버에 연결할 수 없습니다.");
            writer.flush();
            return null;
        }

        JsonNode data = response.get().get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            writer.println("등록된 Squad가 없습니다. 먼저 Squad를 생성해주세요.");
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
        if (selected < 0 || selected >= ids.size()) {
            printCancelled(writer);
            return null;
        }
        return ids.get(selected);
    }

    /**
     * 세션 목록을 조회하여 화살표키 선택 UI로 하나를 고른다.
     *
     * @param ctx    커맨드 컨텍스트
     * @param prompt 선택 프롬프트
     * @return 선택된 세션 ID, 취소 또는 목록 없음 시 null
     */
    private String selectSessionInteractively(CommandContext ctx, String prompt) {
        PrintWriter writer = ctx.writer();
        Optional<JsonNode> response = apiClient.get(API_PATH);

        if (response.isEmpty()) {
            writer.println("서버에 연결할 수 없습니다.");
            writer.flush();
            return null;
        }

        JsonNode data = response.get().get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            writer.println("등록된 세션이 없습니다.");
            writer.flush();
            return null;
        }

        List<String> options = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (JsonNode session : data) {
            String sessionId = session.get("id").asText();
            String status = extractField(session, "status");
            String userPrompt = truncate(extractField(session, "userPrompt"), 30);
            ids.add(sessionId);
            options.add("[" + sessionId + "] " + status + " - " + userPrompt);
        }

        int selected = formReader.readSelection(ctx, prompt, options);
        if (selected < 0 || selected >= ids.size()) {
            printCancelled(writer);
            return null;
        }
        return ids.get(selected);
    }

    /**
     * 세션 ID 문자열을 파싱하고 검증한다.
     *
     * <p>숫자가 아니면 사용법 안내를 출력하고 null을 반환한다.</p>
     *
     * @param writer 출력 PrintWriter
     * @param idArg  세션 ID 문자열
     * @return 파싱된 ID 문자열, 유효하지 않으면 null
     */
    private String parseSessionId(PrintWriter writer, String idArg) {
        String id = idArg.trim();
        try {
            Long.parseLong(id);
            return id;
        } catch (NumberFormatException e) {
            writer.println("사용법: /session [list|start|cancel|status|result|monitor] 또는 /session {id}");
            writer.flush();
            return null;
        }
    }

    /**
     * 여러 줄 텍스트를 들여쓰기하여 출력한다.
     *
     * @param writer 출력 PrintWriter
     * @param text   텍스트
     */
    private void printMultiLineField(PrintWriter writer, String text) {
        if (text == null || text.isEmpty()) {
            writer.println("  (없음)");
            return;
        }
        for (String line : text.split("\n")) {
            writer.println("  " + line);
        }
    }

    private void printCancelled(PrintWriter writer) {
        writer.println("취소되었습니다.");
        writer.flush();
    }

    /**
     * URL에서 Git Provider를 자동 감지한다.
     *
     * @param repoUrl 저장소 URL
     * @return 감지된 Provider 이름, 감지 불가 시 null
     */
    private String detectGitProvider(String repoUrl) {
        String lower = repoUrl.toLowerCase();
        if (lower.contains("github.com")) {
            return "GITHUB";
        }
        if (lower.contains("gitlab.com")) {
            return "GITLAB";
        }
        return null;
    }

    /**
     * Secret 목록을 조회하여 Git Secret을 선택한다.
     *
     * @param ctx 커맨드 컨텍스트
     * @return 선택된 Secret 이름, 건너뛰기 시 null
     */
    private String readGitSecret(CommandContext ctx) {
        PrintWriter writer = ctx.writer();
        Optional<JsonNode> response = apiClient.get("/api/v1/secrets");

        if (response.isEmpty()) {
            return null;
        }

        JsonNode data = response.get().get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            writer.println("  (등록된 Secret이 없습니다. Enter로 건너뛰기)");
            writer.flush();
            return formReader.readLine(ctx, "Git Secret 이름 (선택, Enter로 건너뛰기)");
        }

        List<String> options = new ArrayList<>();
        options.add("(건너뛰기)");
        for (JsonNode secret : data) {
            options.add(extractField(secret, "name"));
        }

        int selected = formReader.readSelection(ctx, "Git Secret 선택 (선택)", options);
        if (selected <= 0 || selected >= options.size()) {
            return null;
        }
        return options.get(selected);
    }

    private String extractField(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return "";
        }
        return node.get(fieldName).asText();
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String singleLine = text.replace('\n', ' ');
        if (singleLine.length() <= maxLen) {
            return singleLine;
        }
        return singleLine.substring(0, maxLen - 3) + "...";
    }

    /**
     * ISO 8601 날짜/시간 문자열을 사용자 친화적으로 포맷한다.
     *
     * <p>{@code T}를 공백으로, 소수점 이하 초를 제거하여 읽기 쉬운 형태로 변환한다.
     * 빈 문자열이면 "(없음)"을 반환한다.</p>
     *
     * @param isoDateTime ISO 날짜/시간 문자열
     * @return 포맷된 날짜/시간 문자열
     */
    private String formatDateTime(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isEmpty()) {
            return "(없음)";
        }
        String formatted = isoDateTime.replace("T", " ");
        int dotIndex = formatted.indexOf('.');
        if (dotIndex > 0) {
            formatted = formatted.substring(0, dotIndex);
        }
        return formatted;
    }
}
