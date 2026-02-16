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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SecretCommandTest {

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

        new SecretCommand(registry, apiClient, tableRenderer, formReader);

        outputBuffer = new StringWriter();
        writer = new PrintWriter(outputBuffer);
        ctx = new CommandContext(null, null, writer);
    }

    @Test
    @DisplayName("secret 커맨드가 레지스트리에 등록된다")
    void registersSecretCommand() {
        assertThat(registry.find("secret")).isPresent();
        assertThat(registry.find("secret").get().description()).contains("Secret");
    }

    @Test
    @DisplayName("/secret list - Secret 목록을 참조 형식과 함께 출력한다")
    void listSecrets() {
        ObjectNode response = createListResponse(
                createSecret(1L, "github-token"),
                createSecret(2L, "slack-webhook")
        );
        when(apiClient.get("/api/v1/secrets")).thenReturn(Optional.of(response));

        executeCommand("list");

        String output = outputBuffer.toString();
        assertThat(output).contains("github-token");
        assertThat(output).contains("ref:secret/github-token");
        assertThat(output).contains("slack-webhook");
        assertThat(output).contains("ref:secret/slack-webhook");
    }

    @Test
    @DisplayName("/secret list - Secret이 없으면 안내 메시지를 출력한다")
    void listSecretsEmpty() {
        ObjectNode response = mapper.createObjectNode();
        response.putArray("data");
        when(apiClient.get("/api/v1/secrets")).thenReturn(Optional.of(response));

        executeCommand("list");

        assertThat(outputBuffer.toString()).contains("등록된 Secret이 없습니다");
    }

    @Test
    @DisplayName("/secret list - 서버 연결 실패 시 안내 메시지를 출력한다")
    void listSecretsServerError() {
        when(apiClient.get("/api/v1/secrets")).thenReturn(Optional.empty());

        executeCommand("list");

        assertThat(outputBuffer.toString()).contains("서버에 연결할 수 없습니다");
    }

    @Test
    @DisplayName("/secret {id} - Secret 상세 정보를 출력한다 (값은 마스킹)")
    void showSecretDetail() {
        ObjectNode response = createDetailResponse(1L, "github-token");
        when(apiClient.get("/api/v1/secrets/1")).thenReturn(Optional.of(response));

        executeCommand("1");

        String output = outputBuffer.toString();
        assertThat(output).contains("Secret 상세");
        assertThat(output).contains("github-token");
        assertThat(output).contains("ref:secret/github-token");
        assertThat(output).contains("********");
    }

    @Test
    @DisplayName("/secret {id} - 존재하지 않는 Secret 조회 시 안내 메시지를 출력한다")
    void showSecretDetailNotFound() {
        when(apiClient.get("/api/v1/secrets/999")).thenReturn(Optional.empty());

        executeCommand("999");

        assertThat(outputBuffer.toString()).contains("Secret을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("/secret create - 이름과 값을 입력하여 Secret을 생성한다")
    void createSecret() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn("github-token");
        when(formReader.readSecret(eq(ctx), eq("값"))).thenReturn("ghp_abc123");
        when(formReader.readConfirm(any(), any())).thenReturn(true);

        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ObjectNode data = mapper.createObjectNode();
        data.put("id", 1);
        response.set("data", data);
        when(apiClient.post(eq("/api/v1/secrets"), any())).thenReturn(Optional.of(response));

        executeCommand("create");

        verify(apiClient).post(eq("/api/v1/secrets"), any());
        String output = outputBuffer.toString();
        assertThat(output).contains("Secret이 생성되었습니다");
        assertThat(output).contains("ref:secret/github-token");
    }

    @Test
    @DisplayName("/secret create - 이름 입력 취소 시 생성을 중단한다")
    void createSecretCancelledAtName() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn(null);

        executeCommand("create");

        verify(apiClient, never()).post(any(), any());
        assertThat(outputBuffer.toString()).contains("취소되었습니다");
    }

    @Test
    @DisplayName("/secret create - 값 입력 취소 시 생성을 중단한다")
    void createSecretCancelledAtValue() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn("my-secret");
        when(formReader.readSecret(eq(ctx), eq("값"))).thenReturn(null);

        executeCommand("create");

        verify(apiClient, never()).post(any(), any());
        assertThat(outputBuffer.toString()).contains("취소되었습니다");
    }

    @Test
    @DisplayName("/secret create - 확인에서 거부하면 생성하지 않는다")
    void createSecretDenied() {
        when(formReader.readLine(eq(ctx), eq("이름"))).thenReturn("my-secret");
        when(formReader.readSecret(eq(ctx), eq("값"))).thenReturn("value123");
        when(formReader.readConfirm(any(), any())).thenReturn(false);

        executeCommand("create");

        verify(apiClient, never()).post(any(), any());
        assertThat(outputBuffer.toString()).contains("취소되었습니다");
    }

    @Test
    @DisplayName("/secret update {id} - 값만 수정한다 (이름 수정 불가)")
    void updateSecret() {
        ObjectNode response = createDetailResponse(1L, "github-token");
        when(apiClient.get("/api/v1/secrets/1")).thenReturn(Optional.of(response));
        when(formReader.readSecret(eq(ctx), eq("새 값"))).thenReturn("new-value");
        when(formReader.readConfirm(any(), eq("수정하시겠습니까?"))).thenReturn(true);

        ObjectNode updateResponse = mapper.createObjectNode();
        updateResponse.put("success", true);
        when(apiClient.put(eq("/api/v1/secrets/1"), any())).thenReturn(Optional.of(updateResponse));

        executeCommand("update 1");

        verify(apiClient).put(eq("/api/v1/secrets/1"), any());
        String output = outputBuffer.toString();
        assertThat(output).contains("수정 불가");
        assertThat(output).contains("Secret이 수정되었습니다");
    }

    @Test
    @DisplayName("/secret update {id} - 존재하지 않는 Secret 수정 시 안내한다")
    void updateSecretNotFound() {
        when(apiClient.get("/api/v1/secrets/999")).thenReturn(Optional.empty());

        executeCommand("update 999");

        assertThat(outputBuffer.toString()).contains("Secret을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("/secret delete {id} - 확인 후 Secret을 삭제한다")
    void deleteSecret() {
        ObjectNode response = createDetailResponse(1L, "github-token");
        when(apiClient.get("/api/v1/secrets/1")).thenReturn(Optional.of(response));
        when(apiClient.delete("/api/v1/secrets/1")).thenReturn(true);
        when(formReader.readConfirm(any(), any())).thenReturn(true);

        executeCommand("delete 1");

        verify(apiClient).delete("/api/v1/secrets/1");
        assertThat(outputBuffer.toString()).contains("Secret이 삭제되었습니다");
    }

    @Test
    @DisplayName("/secret delete {id} - 취소하면 삭제하지 않는다")
    void deleteSecretCancelled() {
        ObjectNode response = createDetailResponse(1L, "github-token");
        when(apiClient.get("/api/v1/secrets/1")).thenReturn(Optional.of(response));
        when(formReader.readConfirm(any(), any())).thenReturn(false);

        executeCommand("delete 1");

        verify(apiClient, never()).delete(any());
        assertThat(outputBuffer.toString()).contains("취소되었습니다");
    }

    @Test
    @DisplayName("/secret delete {id} - 존재하지 않는 Secret 삭제 시 안내한다")
    void deleteSecretNotFound() {
        when(apiClient.get("/api/v1/secrets/999")).thenReturn(Optional.empty());

        executeCommand("delete 999");

        assertThat(outputBuffer.toString()).contains("Secret을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("/secret delete - ID 없이 호출하면 Secret 목록에서 선택한다")
    void deleteSecretNoId() {
        when(apiClient.get("/api/v1/secrets")).thenReturn(Optional.empty());

        executeCommand("delete");

        assertThat(outputBuffer.toString()).contains("서버에 연결할 수 없습니다");
    }

    @Test
    @DisplayName("잘못된 서브커맨드는 사용법을 안내한다")
    void invalidSubcommand() {
        executeCommand("invalid");

        assertThat(outputBuffer.toString()).contains("사용법");
    }

    @Test
    @DisplayName("/secret (빈 인자)는 list와 동일하게 동작한다")
    void emptyArgsDefaultsToList() {
        ObjectNode response = createListResponse(
                createSecret(1L, "github-token")
        );
        when(apiClient.get("/api/v1/secrets")).thenReturn(Optional.of(response));

        executeCommand("");

        assertThat(outputBuffer.toString()).contains("github-token");
    }

    private void executeCommand(String args) {
        registry.find("secret").get().executor().execute(ctx, args);
    }

    private ObjectNode createSecret(Long id, String name) {
        ObjectNode secret = mapper.createObjectNode();
        secret.put("id", id);
        secret.put("name", name);
        secret.put("createdAt", "2026-02-16T10:00:00");
        secret.put("updatedAt", "2026-02-16T10:00:00");
        return secret;
    }

    private ObjectNode createListResponse(ObjectNode... secrets) {
        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ArrayNode data = response.putArray("data");
        for (ObjectNode secret : secrets) {
            data.add(secret);
        }
        return response;
    }

    private ObjectNode createDetailResponse(Long id, String name) {
        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        ObjectNode data = mapper.createObjectNode();
        data.put("id", id);
        data.put("name", name);
        data.put("createdAt", "2026-02-16T10:00:00");
        data.put("updatedAt", "2026-02-16T10:00:00");
        response.set("data", data);
        return response;
    }
}
