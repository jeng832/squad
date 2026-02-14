package com.squad.mcp.gateway;

import java.util.Objects;

/**
 * MCP 도구의 라우팅 정보.
 *
 * <p>LLM에 전달되는 alias 이름과 실제 MCP 서버에서의 원본 도구 이름을
 * 매핑한다. 도구 호출 시 alias로부터 올바른 MCP 서버와 원본 도구 이름을
 * 찾는 데 사용된다.</p>
 *
 * @param alias            LLM에 전달되는 도구 이름 ({@code mcpName__toolName} 형식)
 * @param mcpName          MCP 서버 이름
 * @param originalToolName MCP 서버에서의 원본 도구 이름
 */
public record ToolRoute(
        String alias,
        String mcpName,
        String originalToolName
) {

    private static final String SEPARATOR = "__";

    /**
     * MCP 이름과 도구 이름으로 {@code ToolRoute}를 생성한다.
     *
     * <p>alias는 {@code mcpName__toolName} 형식으로 자동 생성된다.</p>
     *
     * @param mcpName  MCP 서버 이름
     * @param toolName 원본 도구 이름
     * @return 라우팅 정보
     * @throws NullPointerException     mcpName 또는 toolName이 null인 경우
     * @throws IllegalArgumentException mcpName 또는 toolName이 blank인 경우
     */
    public static ToolRoute of(String mcpName, String toolName) {
        Objects.requireNonNull(mcpName, "mcpName은 null일 수 없습니다");
        Objects.requireNonNull(toolName, "toolName은 null일 수 없습니다");
        if (mcpName.isBlank()) {
            throw new IllegalArgumentException("mcpName은 blank일 수 없습니다");
        }
        if (toolName.isBlank()) {
            throw new IllegalArgumentException("toolName은 blank일 수 없습니다");
        }
        return new ToolRoute(aliasOf(mcpName, toolName), mcpName, toolName);
    }

    /**
     * MCP 이름과 도구 이름으로 alias 문자열만 생성한다.
     *
     * @param mcpName  MCP 서버 이름
     * @param toolName 원본 도구 이름
     * @return alias 문자열 ({@code mcpName__toolName})
     */
    static String aliasOf(String mcpName, String toolName) {
        return mcpName + SEPARATOR + toolName;
    }
}
