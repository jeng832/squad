package com.squad.mcp.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ToolRoute")
class ToolRouteTest {

    @Test
    @DisplayName("of()로 생성 시 mcpName__toolName 형식의 alias가 생성된다")
    void createsAliasWithDoubleUnderscore() {
        ToolRoute route = ToolRoute.of("github", "get_issue");

        assertThat(route.alias()).isEqualTo("github__get_issue");
        assertThat(route.mcpName()).isEqualTo("github");
        assertThat(route.originalToolName()).isEqualTo("get_issue");
    }

    @Test
    @DisplayName("서로 다른 MCP의 동일 도구 이름도 alias가 구분된다")
    void distinguishesSameToolFromDifferentMcps() {
        ToolRoute githubRoute = ToolRoute.of("github", "search");
        ToolRoute slackRoute = ToolRoute.of("slack", "search");

        assertThat(githubRoute.alias()).isNotEqualTo(slackRoute.alias());
        assertThat(githubRoute.alias()).isEqualTo("github__search");
        assertThat(slackRoute.alias()).isEqualTo("slack__search");
    }

    @Test
    @DisplayName("mcpName이 null이면 NullPointerException이 발생한다")
    void throwsOnNullMcpName() {
        assertThatThrownBy(() -> ToolRoute.of(null, "tool"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("toolName이 blank이면 IllegalArgumentException이 발생한다")
    void throwsOnBlankToolName() {
        assertThatThrownBy(() -> ToolRoute.of("mcp", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
