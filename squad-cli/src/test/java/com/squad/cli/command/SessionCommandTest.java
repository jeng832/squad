package com.squad.cli.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.squad.cli.api.SquadApiClient;
import com.squad.cli.config.CliConfig;
import com.squad.cli.form.InteractiveFormReader;
import com.squad.cli.shell.CommandContext;
import com.squad.cli.shell.CommandRegistry;
import com.squad.cli.ui.TableRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SessionCommandTest {

    private CommandRegistry registry;
    private SquadApiClient apiClient;
    private TableRenderer tableRenderer;
    private InteractiveFormReader formReader;
    private WebSocketStompClient stompClient;
    private CliConfig cliConfig;

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
        stompClient = mock(WebSocketStompClient.class);
        cliConfig = mock(CliConfig.class);
        when(cliConfig.getServerUrl()).thenReturn("http://localhost:8080");

        new SessionCommand(registry, apiClient, tableRenderer, formReader, stompClient, cliConfig);

        outputBuffer = new StringWriter();
        writer = new PrintWriter(outputBuffer);
        ctx = new CommandContext(null, null, writer);
    }

    @Test
    @DisplayName("session 커맨드가 레지스트리에 등록된다")
    void registersSessionCommand() {
        assertThat(registry.find("session")).isPresent();
        assertThat(registry.find("session").get().description()).contains("세션");
    }

    @Test
    @DisplayName("session 커맨드에 서브커맨드가 등록된다")
    void registersSubcommands() {
        var entry = registry.find("session").get();
        assertThat(entry.hasSubcommands()).isTrue();
        assertThat(entry.subcommands()).hasSize(6);
    }

    @Nested
    @DisplayName("/session list")
    class ListTests {

        @Test
        @DisplayName("세션 목록을 테이블로 출력한다")
        void listSessions() {
            ObjectNode response = createListResponse(
                    createSession(1L, 10L, "RUNNING", "분석 요청", null),
                    createSession(2L, 20L, "COMPLETED", "번역 요청", "번역 완료")
            );
            when(apiClient.get("/api/v1/sessions")).thenReturn(Optional.of(response));

            executeCommand("list");

            String output = outputBuffer.toString();
            assertThat(output).contains("RUNNING");
            assertThat(output).contains("COMPLETED");
            assertThat(output).contains("분석 요청");
        }

        @Test
        @DisplayName("세션이 없으면 안내 메시지를 출력한다")
        void listSessionsEmpty() {
            ObjectNode response = mapper.createObjectNode();
            response.putArray("data");
            when(apiClient.get("/api/v1/sessions")).thenReturn(Optional.of(response));

            executeCommand("list");

            assertThat(outputBuffer.toString()).contains("등록된 세션이 없습니다");
        }

        @Test
        @DisplayName("서버 연결 실패 시 안내 메시지를 출력한다")
        void listSessionsServerError() {
            when(apiClient.get("/api/v1/sessions")).thenReturn(Optional.empty());

            executeCommand("list");

            assertThat(outputBuffer.toString()).contains("서버에 연결할 수 없습니다");
        }

        @Test
        @DisplayName("빈 인자는 list와 동일하게 동작한다")
        void emptyArgsDefaultsToList() {
            ObjectNode response = createListResponse(
                    createSession(1L, 10L, "PENDING", "테스트", null)
            );
            when(apiClient.get("/api/v1/sessions")).thenReturn(Optional.of(response));

            executeCommand("");

            assertThat(outputBuffer.toString()).contains("PENDING");
        }
    }

    @Nested
    @DisplayName("/session start")
    class StartTests {

        @Test
        @DisplayName("Squad 선택 후 프롬프트 입력으로 세션을 시작한다")
        void startSession() {
            // Squad 목록
            ObjectNode squadsResponse = createSquadListResponse(
                    createSquad(10L, "분석 Squad", "데이터 분석")
            );
            when(apiClient.get("/api/v1/squads")).thenReturn(Optional.of(squadsResponse));
            when(formReader.readSelection(eq(ctx), eq("Squad 선택"), any())).thenReturn(0);

            // 프롬프트
            when(formReader.readMultiLine(eq(ctx), eq("프롬프트 입력"))).thenReturn("데이터를 분석해주세요");

            // 확인
            when(formReader.readConfirm(any(), any())).thenReturn(true);

            // 생성 응답
            ObjectNode createResponse = createDetailResponse(1L, 10L, "PENDING", "데이터를 분석해주세요", null);
            when(apiClient.post(eq("/api/v1/sessions"), any())).thenReturn(Optional.of(createResponse));

            // 시작 응답
            ObjectNode startResponse = createDetailResponse(1L, 10L, "RUNNING", "데이터를 분석해주세요", null);
            when(apiClient.post(eq("/api/v1/sessions/1/start"), any())).thenReturn(Optional.of(startResponse));

            executeCommand("start");

            verify(apiClient).post(eq("/api/v1/sessions"), any());
            verify(apiClient).post(eq("/api/v1/sessions/1/start"), any());
            String output = outputBuffer.toString();
            assertThat(output).contains("세션이 생성되었습니다");
            assertThat(output).contains("세션이 시작되었습니다");
        }

        @Test
        @DisplayName("Squad 선택 취소 시 세션을 시작하지 않는다")
        void startSessionSquadCancelled() {
            ObjectNode squadsResponse = createSquadListResponse(
                    createSquad(10L, "분석 Squad", "")
            );
            when(apiClient.get("/api/v1/squads")).thenReturn(Optional.of(squadsResponse));
            when(formReader.readSelection(eq(ctx), eq("Squad 선택"), any())).thenReturn(-1);

            executeCommand("start");

            verify(apiClient, never()).post(eq("/api/v1/sessions"), any());
            assertThat(outputBuffer.toString()).contains("취소되었습니다");
        }

        @Test
        @DisplayName("프롬프트 입력 취소 시 세션을 시작하지 않는다")
        void startSessionPromptCancelled() {
            ObjectNode squadsResponse = createSquadListResponse(
                    createSquad(10L, "Squad", "")
            );
            when(apiClient.get("/api/v1/squads")).thenReturn(Optional.of(squadsResponse));
            when(formReader.readSelection(eq(ctx), eq("Squad 선택"), any())).thenReturn(0);
            when(formReader.readMultiLine(eq(ctx), eq("프롬프트 입력"))).thenReturn(null);

            executeCommand("start");

            verify(apiClient, never()).post(eq("/api/v1/sessions"), any());
            assertThat(outputBuffer.toString()).contains("취소되었습니다");
        }

        @Test
        @DisplayName("공백만 입력한 프롬프트는 거부한다")
        void startSessionBlankPrompt() {
            ObjectNode squadsResponse = createSquadListResponse(
                    createSquad(10L, "Squad", "")
            );
            when(apiClient.get("/api/v1/squads")).thenReturn(Optional.of(squadsResponse));
            when(formReader.readSelection(eq(ctx), eq("Squad 선택"), any())).thenReturn(0);
            when(formReader.readMultiLine(eq(ctx), eq("프롬프트 입력"))).thenReturn("   ");

            executeCommand("start");

            verify(apiClient, never()).post(eq("/api/v1/sessions"), any());
            assertThat(outputBuffer.toString()).contains("프롬프트를 입력해주세요");
        }

        @Test
        @DisplayName("빈 프롬프트 입력 시 안내 메시지를 출력한다")
        void startSessionEmptyPrompt() {
            ObjectNode squadsResponse = createSquadListResponse(
                    createSquad(10L, "Squad", "")
            );
            when(apiClient.get("/api/v1/squads")).thenReturn(Optional.of(squadsResponse));
            when(formReader.readSelection(eq(ctx), eq("Squad 선택"), any())).thenReturn(0);
            when(formReader.readMultiLine(eq(ctx), eq("프롬프트 입력"))).thenReturn("");

            executeCommand("start");

            verify(apiClient, never()).post(eq("/api/v1/sessions"), any());
            assertThat(outputBuffer.toString()).contains("프롬프트를 입력해주세요");
        }

        @Test
        @DisplayName("확인에서 취소 시 세션을 시작하지 않는다")
        void startSessionConfirmCancelled() {
            ObjectNode squadsResponse = createSquadListResponse(
                    createSquad(10L, "Squad", "")
            );
            when(apiClient.get("/api/v1/squads")).thenReturn(Optional.of(squadsResponse));
            when(formReader.readSelection(eq(ctx), eq("Squad 선택"), any())).thenReturn(0);
            when(formReader.readMultiLine(eq(ctx), eq("프롬프트 입력"))).thenReturn("프롬프트");
            when(formReader.readConfirm(any(), any())).thenReturn(false);

            executeCommand("start");

            verify(apiClient, never()).post(eq("/api/v1/sessions"), any());
            assertThat(outputBuffer.toString()).contains("취소되었습니다");
        }

        @Test
        @DisplayName("Squad가 없으면 안내 메시지를 출력한다")
        void startSessionNoSquads() {
            ObjectNode squadsResponse = mapper.createObjectNode();
            squadsResponse.putArray("data");
            when(apiClient.get("/api/v1/squads")).thenReturn(Optional.of(squadsResponse));

            executeCommand("start");

            verify(apiClient, never()).post(eq("/api/v1/sessions"), any());
            assertThat(outputBuffer.toString()).contains("등록된 Squad가 없습니다");
        }

        @Test
        @DisplayName("세션 생성 실패 시 안내 메시지를 출력한다")
        void startSessionCreateFailed() {
            ObjectNode squadsResponse = createSquadListResponse(
                    createSquad(10L, "Squad", "")
            );
            when(apiClient.get("/api/v1/squads")).thenReturn(Optional.of(squadsResponse));
            when(formReader.readSelection(eq(ctx), eq("Squad 선택"), any())).thenReturn(0);
            when(formReader.readMultiLine(eq(ctx), eq("프롬프트 입력"))).thenReturn("프롬프트");
            when(formReader.readConfirm(any(), any())).thenReturn(true);
            when(apiClient.post(eq("/api/v1/sessions"), any())).thenReturn(Optional.empty());

            executeCommand("start");

            assertThat(outputBuffer.toString()).contains("세션 생성에 실패했습니다");
        }

        @Test
        @DisplayName("세션 시작 실패 시 상태 확인 안내를 출력한다")
        void startSessionStartFailed() {
            ObjectNode squadsResponse = createSquadListResponse(
                    createSquad(10L, "Squad", "")
            );
            when(apiClient.get("/api/v1/squads")).thenReturn(Optional.of(squadsResponse));
            when(formReader.readSelection(eq(ctx), eq("Squad 선택"), any())).thenReturn(0);
            when(formReader.readMultiLine(eq(ctx), eq("프롬프트 입력"))).thenReturn("프롬프트");
            when(formReader.readConfirm(any(), any())).thenReturn(true);

            ObjectNode createResponse = createDetailResponse(1L, 10L, "PENDING", "프롬프트", null);
            when(apiClient.post(eq("/api/v1/sessions"), any())).thenReturn(Optional.of(createResponse));
            when(apiClient.post(eq("/api/v1/sessions/1/start"), any())).thenReturn(Optional.empty());

            executeCommand("start");

            String output = outputBuffer.toString();
            assertThat(output).contains("세션이 생성되었습니다");
            assertThat(output).contains("세션 시작에 실패했습니다");
        }

        @Test
        @DisplayName("생성 응답에 ID가 없으면 안내 메시지를 출력한다")
        void startSessionCreateResponseNoId() {
            ObjectNode squadsResponse = createSquadListResponse(
                    createSquad(10L, "Squad", "")
            );
            when(apiClient.get("/api/v1/squads")).thenReturn(Optional.of(squadsResponse));
            when(formReader.readSelection(eq(ctx), eq("Squad 선택"), any())).thenReturn(0);
            when(formReader.readMultiLine(eq(ctx), eq("프롬프트 입력"))).thenReturn("프롬프트");
            when(formReader.readConfirm(any(), any())).thenReturn(true);

            // data는 있지만 id가 null
            ObjectNode response = mapper.createObjectNode();
            ObjectNode data = mapper.createObjectNode();
            data.putNull("id");
            response.set("data", data);
            when(apiClient.post(eq("/api/v1/sessions"), any())).thenReturn(Optional.of(response));

            executeCommand("start");

            assertThat(outputBuffer.toString()).contains("ID를 읽을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("/session cancel")
    class CancelTests {

        @Test
        @DisplayName("ID 지정으로 세션을 취소한다")
        void cancelSessionWithId() {
            ObjectNode existing = createDetailResponse(1L, 10L, "RUNNING", "프롬프트", null);
            when(apiClient.get("/api/v1/sessions/1")).thenReturn(Optional.of(existing));
            when(formReader.readConfirm(any(), any())).thenReturn(true);

            ObjectNode cancelResponse = createDetailResponse(1L, 10L, "CANCELLED", "프롬프트", null);
            when(apiClient.post(eq("/api/v1/sessions/1/cancel"), any())).thenReturn(Optional.of(cancelResponse));

            executeCommand("cancel 1");

            verify(apiClient).post(eq("/api/v1/sessions/1/cancel"), any());
            assertThat(outputBuffer.toString()).contains("세션이 취소되었습니다");
        }

        @Test
        @DisplayName("확인에서 취소하면 세션을 취소하지 않는다")
        void cancelSessionDenied() {
            ObjectNode existing = createDetailResponse(1L, 10L, "RUNNING", "프롬프트", null);
            when(apiClient.get("/api/v1/sessions/1")).thenReturn(Optional.of(existing));
            when(formReader.readConfirm(any(), any())).thenReturn(false);

            executeCommand("cancel 1");

            verify(apiClient, never()).post(eq("/api/v1/sessions/1/cancel"), any());
            assertThat(outputBuffer.toString()).contains("취소되었습니다");
        }

        @Test
        @DisplayName("이미 완료된 세션은 취소할 수 없다")
        void cancelCompletedSession() {
            ObjectNode existing = createDetailResponse(1L, 10L, "COMPLETED", "프롬프트", "결과");
            when(apiClient.get("/api/v1/sessions/1")).thenReturn(Optional.of(existing));

            executeCommand("cancel 1");

            verify(apiClient, never()).post(eq("/api/v1/sessions/1/cancel"), any());
            assertThat(outputBuffer.toString()).contains("이미 COMPLETED 상태인 세션입니다");
        }

        @Test
        @DisplayName("이미 취소된 세션은 다시 취소할 수 없다")
        void cancelAlreadyCancelledSession() {
            ObjectNode existing = createDetailResponse(1L, 10L, "CANCELLED", "프롬프트", null);
            when(apiClient.get("/api/v1/sessions/1")).thenReturn(Optional.of(existing));

            executeCommand("cancel 1");

            verify(apiClient, never()).post(eq("/api/v1/sessions/1/cancel"), any());
            assertThat(outputBuffer.toString()).contains("이미 CANCELLED 상태인 세션입니다");
        }

        @Test
        @DisplayName("존재하지 않는 세션 취소 시 안내 메시지를 출력한다")
        void cancelSessionNotFound() {
            when(apiClient.get("/api/v1/sessions/999")).thenReturn(Optional.empty());

            executeCommand("cancel 999");

            assertThat(outputBuffer.toString()).contains("세션을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("ID 없이 호출하면 세션 목록에서 선택한다")
        void cancelSessionNoId() {
            when(apiClient.get("/api/v1/sessions")).thenReturn(Optional.empty());

            executeCommand("cancel");

            assertThat(outputBuffer.toString()).contains("서버에 연결할 수 없습니다");
        }

        @Test
        @DisplayName("비숫자 ID 입력 시 사용법을 안내한다")
        void cancelSessionInvalidId() {
            executeCommand("cancel abc");

            assertThat(outputBuffer.toString()).contains("사용법");
        }
    }

    @Nested
    @DisplayName("/session status")
    class StatusTests {

        @Test
        @DisplayName("세션 상세 정보를 출력한다")
        void showSessionStatus() {
            ObjectNode response = createDetailResponse(1L, 10L, "RUNNING", "분석 요청\n세부사항", null);
            when(apiClient.get("/api/v1/sessions/1")).thenReturn(Optional.of(response));

            executeCommand("status 1");

            String output = outputBuffer.toString();
            assertThat(output).contains("세션 상세");
            assertThat(output).contains("RUNNING");
            assertThat(output).contains("분석 요청");
        }

        @Test
        @DisplayName("존재하지 않는 세션 상태 확인 시 안내 메시지를 출력한다")
        void showSessionStatusNotFound() {
            when(apiClient.get("/api/v1/sessions/999")).thenReturn(Optional.empty());

            executeCommand("status 999");

            assertThat(outputBuffer.toString()).contains("세션을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("숫자 ID를 직접 입력하면 status로 동작한다")
        void numericIdDefaultsToStatus() {
            ObjectNode response = createDetailResponse(1L, 10L, "PENDING", "프롬프트", null);
            when(apiClient.get("/api/v1/sessions/1")).thenReturn(Optional.of(response));

            executeCommand("1");

            assertThat(outputBuffer.toString()).contains("세션 상세");
        }

        @Test
        @DisplayName("잘못된 서브커맨드는 사용법을 안내한다")
        void invalidSubcommand() {
            executeCommand("invalid");

            assertThat(outputBuffer.toString()).contains("사용법");
        }

        @Test
        @DisplayName("ID 없이 호출하면 세션 목록에서 선택한다")
        void statusNoId() {
            when(apiClient.get("/api/v1/sessions")).thenReturn(Optional.empty());

            executeCommand("status");

            assertThat(outputBuffer.toString()).contains("서버에 연결할 수 없습니다");
        }
    }

    @Nested
    @DisplayName("/session result")
    class ResultTests {

        @Test
        @DisplayName("완료된 세션의 결과를 출력한다")
        void showSessionResult() {
            ObjectNode response = createDetailResponse(1L, 10L, "COMPLETED", "프롬프트", "분석 결과입니다.\n상세 내용.");
            when(apiClient.get("/api/v1/sessions/1")).thenReturn(Optional.of(response));

            executeCommand("result 1");

            String output = outputBuffer.toString();
            assertThat(output).contains("세션 결과");
            assertThat(output).contains("COMPLETED");
            assertThat(output).contains("분석 결과입니다.");
        }

        @Test
        @DisplayName("진행 중인 세션은 미완료 메시지를 출력한다")
        void showSessionResultRunning() {
            ObjectNode response = createDetailResponse(1L, 10L, "RUNNING", "프롬프트", null);
            when(apiClient.get("/api/v1/sessions/1")).thenReturn(Optional.of(response));

            executeCommand("result 1");

            assertThat(outputBuffer.toString()).contains("세션이 아직 완료되지 않았습니다");
        }

        @Test
        @DisplayName("취소된 세션은 결과 없음 메시지를 출력한다")
        void showSessionResultCancelled() {
            ObjectNode response = createDetailResponse(1L, 10L, "CANCELLED", "프롬프트", null);
            when(apiClient.get("/api/v1/sessions/1")).thenReturn(Optional.of(response));

            executeCommand("result 1");

            assertThat(outputBuffer.toString()).contains("결과가 없습니다");
        }

        @Test
        @DisplayName("존재하지 않는 세션 결과 조회 시 안내 메시지를 출력한다")
        void showSessionResultNotFound() {
            when(apiClient.get("/api/v1/sessions/999")).thenReturn(Optional.empty());

            executeCommand("result 999");

            assertThat(outputBuffer.toString()).contains("세션을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("ID 없이 호출하면 세션 목록에서 선택한다")
        void resultNoId() {
            ObjectNode sessionsResponse = createListResponse(
                    createSession(1L, 10L, "COMPLETED", "프롬프트", "결과")
            );
            when(apiClient.get("/api/v1/sessions")).thenReturn(Optional.of(sessionsResponse));
            when(formReader.readSelection(eq(ctx), eq("결과를 조회할 세션 선택"), any())).thenReturn(0);

            ObjectNode response = createDetailResponse(1L, 10L, "COMPLETED", "프롬프트", "분석 결과");
            when(apiClient.get("/api/v1/sessions/1")).thenReturn(Optional.of(response));

            executeCommand("result");

            assertThat(outputBuffer.toString()).contains("분석 결과");
        }

        @Test
        @DisplayName("비숫자 ID 입력 시 사용법을 안내한다")
        void resultInvalidId() {
            executeCommand("result abc");

            assertThat(outputBuffer.toString()).contains("사용법");
        }
    }

    @Nested
    @DisplayName("/session monitor")
    class MonitorTests {

        @Test
        @DisplayName("WebSocket 연결 타임아웃 시 오류 메시지를 출력한다")
        void monitorConnectionTimeout() {
            CompletableFuture<StompSession> future = new CompletableFuture<>();
            future.completeExceptionally(new TimeoutException("연결 시간 초과"));
            when(stompClient.connectAsync(eq("ws://localhost:8080/ws"), any())).thenReturn(future);

            executeCommand("monitor 1");

            assertThat(outputBuffer.toString()).contains("[오류]");
        }

        @Test
        @DisplayName("비숫자 ID 입력 시 사용법을 안내한다")
        void monitorInvalidId() {
            executeCommand("monitor abc");

            assertThat(outputBuffer.toString()).contains("사용법");
        }

        @Test
        @DisplayName("ID 없이 호출하면 세션 목록에서 선택한다")
        void monitorNoId() {
            when(apiClient.get("/api/v1/sessions")).thenReturn(Optional.empty());

            executeCommand("monitor");

            assertThat(outputBuffer.toString()).contains("서버에 연결할 수 없습니다");
        }

        @Test
        @DisplayName("https URL이 wss로 변환된다")
        void monitorHttpsToWss() {
            when(cliConfig.getServerUrl()).thenReturn("https://squad.example.com");
            CompletableFuture<StompSession> future = new CompletableFuture<>();
            future.completeExceptionally(new TimeoutException("timeout"));
            when(stompClient.connectAsync(eq("wss://squad.example.com/ws"), any())).thenReturn(future);

            executeCommand("monitor 1");

            verify(stompClient).connectAsync(eq("wss://squad.example.com/ws"), any());
        }

        @Test
        @DisplayName("세션 선택 취소 시 모니터링을 시작하지 않는다")
        void monitorCancelledSelection() {
            ObjectNode sessionsResponse = createListResponse(
                    createSession(1L, 10L, "RUNNING", "프롬프트", null)
            );
            when(apiClient.get("/api/v1/sessions")).thenReturn(Optional.of(sessionsResponse));
            when(formReader.readSelection(eq(ctx), eq("모니터링할 세션 선택"), any())).thenReturn(-1);

            executeCommand("monitor");

            verify(stompClient, never()).connectAsync(any(), any());
            assertThat(outputBuffer.toString()).contains("취소되었습니다");
        }
    }

    private void executeCommand(String args) {
        registry.find("session").get().executor().execute(ctx, args);
    }

    private ObjectNode createSession(Long id, Long squadId, String status,
                                      String userPrompt, String result) {
        ObjectNode session = mapper.createObjectNode();
        session.put("id", id);
        session.put("squadId", squadId);
        session.put("status", status);
        session.put("userPrompt", userPrompt);
        if (result != null) {
            session.put("result", result);
        } else {
            session.putNull("result");
        }
        session.put("createdAt", "2026-02-16T10:00:00");
        session.putNull("startedAt");
        session.putNull("completedAt");
        return session;
    }

    private ObjectNode createListResponse(ObjectNode... sessions) {
        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ArrayNode data = response.putArray("data");
        for (ObjectNode session : sessions) {
            data.add(session);
        }
        return response;
    }

    private ObjectNode createDetailResponse(Long id, Long squadId, String status,
                                             String userPrompt, String result) {
        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ObjectNode data = mapper.createObjectNode();
        data.put("id", id);
        data.put("squadId", squadId);
        data.put("status", status);
        data.put("userPrompt", userPrompt);
        if (result != null) {
            data.put("result", result);
        } else {
            data.putNull("result");
        }
        data.put("createdAt", "2026-02-16T10:00:00");
        data.putNull("startedAt");
        data.putNull("completedAt");
        response.set("data", data);
        return response;
    }

    private ObjectNode createSquad(Long id, String name, String description) {
        ObjectNode squad = mapper.createObjectNode();
        squad.put("id", id);
        squad.put("name", name);
        squad.put("description", description);
        return squad;
    }

    private ObjectNode createSquadListResponse(ObjectNode... squads) {
        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ArrayNode data = response.putArray("data");
        for (ObjectNode squad : squads) {
            data.add(squad);
        }
        return response;
    }
}
