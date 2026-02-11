package com.squad.mcp.process;

import com.squad.mcp.domain.Mcp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("McpProcessManager 단위 테스트")
class McpProcessManagerTest {

    private final McpProcessManager processManager = new McpProcessManager();

    @AfterEach
    void tearDown() {
        processManager.stopAll();
    }

    private McpConfig createEchoConfig(String name) {
        Mcp mcp = Mcp.builder()
                .id(1L)
                .name(name)
                .config(Map.of(
                        "command", "cat",
                        "args", List.of()
                ))
                .build();
        return McpConfig.from(mcp);
    }

    @Test
    @DisplayName("MCP 프로세스를 시작하고 연결을 반환한다")
    void startReturnsConnection() {
        McpConfig config = createEchoConfig("test-mcp");

        McpConnection connection = processManager.start(config);

        assertThat(connection).isNotNull();
        assertThat(connection.getName()).isEqualTo("test-mcp");
        assertThat(connection.isAlive()).isTrue();
        assertThat(connection.getStdin()).isNotNull();
        assertThat(connection.getStdout()).isNotNull();
        assertThat(connection.getStderr()).isNotNull();
    }

    @Test
    @DisplayName("getConnection()으로 활성 연결을 조회할 수 있다")
    void getConnectionReturnsActiveConnection() {
        McpConfig config = createEchoConfig("test-mcp");
        processManager.start(config);

        Optional<McpConnection> connection = processManager.getConnection("test-mcp");

        assertThat(connection).isPresent();
        assertThat(connection.get().getName()).isEqualTo("test-mcp");
    }

    @Test
    @DisplayName("존재하지 않는 연결 조회 시 empty를 반환한다")
    void getConnectionReturnsEmptyForUnknown() {
        Optional<McpConnection> connection = processManager.getConnection("unknown");

        assertThat(connection).isEmpty();
    }

    @Test
    @DisplayName("stop()으로 특정 연결을 종료한다")
    void stopClosesConnection() {
        McpConfig config = createEchoConfig("test-mcp");
        McpConnection connection = processManager.start(config);

        processManager.stop("test-mcp");

        assertThat(processManager.getConnection("test-mcp")).isEmpty();
        assertThat(connection.isAlive()).isFalse();
    }

    @Test
    @DisplayName("stopAll()로 모든 연결을 종료한다")
    void stopAllClosesAllConnections() {
        McpConnection conn1 = processManager.start(createEchoConfig("mcp-1"));
        McpConnection conn2 = processManager.start(createEchoConfig("mcp-2"));

        processManager.stopAll();

        assertThat(processManager.getActiveConnectionCount()).isZero();
        assertThat(conn1.isAlive()).isFalse();
        assertThat(conn2.isAlive()).isFalse();
    }

    @Test
    @DisplayName("동일 이름의 MCP를 중복 시작하면 기존 연결을 종료한다")
    void startDuplicateStopsExisting() {
        McpConnection first = processManager.start(createEchoConfig("test-mcp"));

        McpConnection second = processManager.start(createEchoConfig("test-mcp"));

        assertThat(first.isAlive()).isFalse();
        assertThat(second.isAlive()).isTrue();
        assertThat(processManager.getActiveConnectionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("존재하지 않는 command로 시작하면 McpProcessException 발생")
    void startWithInvalidCommandThrowsException() {
        Mcp mcp = Mcp.builder()
                .id(1L)
                .name("invalid")
                .config(Map.of("command", "nonexistent-command-xyz"))
                .build();
        McpConfig config = McpConfig.from(mcp);

        assertThatThrownBy(() -> processManager.start(config))
                .isInstanceOf(McpProcessException.class)
                .hasMessageContaining("MCP 프로세스 시작 실패");
    }

    @Test
    @DisplayName("getActiveConnectionCount()는 활성 연결 수를 반환한다")
    void getActiveConnectionCountReturnsCorrectCount() {
        assertThat(processManager.getActiveConnectionCount()).isZero();

        processManager.start(createEchoConfig("mcp-1"));
        assertThat(processManager.getActiveConnectionCount()).isEqualTo(1);

        processManager.start(createEchoConfig("mcp-2"));
        assertThat(processManager.getActiveConnectionCount()).isEqualTo(2);

        processManager.stop("mcp-1");
        assertThat(processManager.getActiveConnectionCount()).isEqualTo(1);
    }
}
