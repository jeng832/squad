package com.squad.mcp.process;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("McpConnection 단위 테스트")
class McpConnectionTest {

    @Test
    @DisplayName("실행 중인 프로세스로부터 연결을 생성한다")
    void ofCreatesConnection() throws IOException {
        Process process = new ProcessBuilder("cat").start();

        McpConnection connection = McpConnection.of("test", process);

        assertThat(connection.getName()).isEqualTo("test");
        assertThat(connection.isAlive()).isTrue();
        assertThat(connection.getStdin()).isNotNull();
        assertThat(connection.getStdout()).isNotNull();
        assertThat(connection.getStderr()).isNotNull();

        connection.close();
    }

    @Test
    @DisplayName("close() 호출 시 프로세스가 종료된다")
    void closeTerminatesProcess() throws IOException {
        Process process = new ProcessBuilder("cat").start();
        McpConnection connection = McpConnection.of("test", process);

        connection.close();

        assertThat(connection.isAlive()).isFalse();
    }

    @Test
    @DisplayName("이미 종료된 프로세스에 close()를 호출해도 안전하다")
    void closeOnTerminatedProcessIsSafe() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("echo", "hello").start();
        McpConnection connection = McpConnection.of("test", process);

        process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        connection.close();

        assertThat(connection.isAlive()).isFalse();
    }
}
