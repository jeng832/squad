package com.squad.mcp.process;

import com.squad.mcp.domain.Mcp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("McpConfig 단위 테스트")
class McpConfigTest {

    @Test
    @DisplayName("Mcp 엔티티로부터 McpConfig를 생성한다")
    void fromMcp() {
        Mcp mcp = Mcp.builder()
                .id(1L)
                .name("github")
                .config(Map.of(
                        "type", "stdio",
                        "command", "npx",
                        "args", List.of("-y", "@modelcontextprotocol/server-github"),
                        "env", Map.of("GITHUB_TOKEN", "test-token")
                ))
                .build();

        McpConfig config = McpConfig.from(mcp);

        assertThat(config.getName()).isEqualTo("github");
        assertThat(config.getCommand()).isEqualTo("npx");
        assertThat(config.getArgs()).containsExactly("-y", "@modelcontextprotocol/server-github");
        assertThat(config.getEnv()).containsEntry("GITHUB_TOKEN", "test-token");
    }

    @Test
    @DisplayName("args가 없으면 빈 리스트로 생성한다")
    void fromMcpWithoutArgs() {
        Mcp mcp = Mcp.builder()
                .id(1L)
                .name("simple")
                .config(Map.of("command", "echo"))
                .build();

        McpConfig config = McpConfig.from(mcp);

        assertThat(config.getArgs()).isEmpty();
        assertThat(config.getEnv()).isEmpty();
    }

    @Test
    @DisplayName("command가 없으면 IllegalArgumentException 발생")
    void fromMcpWithoutCommand() {
        Mcp mcp = Mcp.builder()
                .id(1L)
                .name("invalid")
                .config(Map.of("type", "stdio"))
                .build();

        assertThatThrownBy(() -> McpConfig.from(mcp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command");
    }

    @Test
    @DisplayName("buildCommandLine()은 command와 args를 결합한다")
    void buildCommandLine() {
        Mcp mcp = Mcp.builder()
                .id(1L)
                .name("github")
                .config(Map.of(
                        "command", "npx",
                        "args", List.of("-y", "server-github")
                ))
                .build();

        McpConfig config = McpConfig.from(mcp);

        assertThat(config.buildCommandLine()).containsExactly("npx", "-y", "server-github");
    }

    @Test
    @DisplayName("command가 String이 아니면 IllegalArgumentException 발생")
    void fromMcpWithNonStringCommand() {
        Mcp mcp = Mcp.builder()
                .id(1L)
                .name("invalid")
                .config(Map.of("command", 123))
                .build();

        assertThatThrownBy(() -> McpConfig.from(mcp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command");
    }

    @Test
    @DisplayName("args가 배열이 아니면 IllegalArgumentException 발생")
    void fromMcpWithNonListArgs() {
        Mcp mcp = Mcp.builder()
                .id(1L)
                .name("invalid")
                .config(Map.of("command", "npx", "args", "not-a-list"))
                .build();

        assertThatThrownBy(() -> McpConfig.from(mcp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("args");
    }

    @Test
    @DisplayName("env가 Map이 아니면 IllegalArgumentException 발생")
    void fromMcpWithNonMapEnv() {
        Mcp mcp = Mcp.builder()
                .id(1L)
                .name("invalid")
                .config(Map.of("command", "npx", "env", "not-a-map"))
                .build();

        assertThatThrownBy(() -> McpConfig.from(mcp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("env");
    }
}
