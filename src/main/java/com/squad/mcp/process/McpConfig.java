package com.squad.mcp.process;

import com.squad.mcp.domain.Mcp;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.*;

/**
 * MCP 서버 프로세스 실행에 필요한 설정 값 객체.
 *
 * <p>{@link Mcp} 엔티티의 JSON config에서 프로세스 실행에 필요한
 * command, args, env 정보를 추출한다.</p>
 *
 * <p>MCP config JSON 형식:</p>
 * <pre>{@code
 * {
 *   "type": "stdio",
 *   "command": "npx",
 *   "args": ["-y", "@modelcontextprotocol/server-github"],
 *   "env": { "GITHUB_TOKEN": "..." }
 * }
 * }</pre>
 *
 * @see Mcp
 * @see McpProcessManager
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class McpConfig {

    private final String name;
    private final String command;
    private final List<String> args;
    private final Map<String, String> env;

    /**
     * {@link Mcp} 엔티티로부터 {@code McpConfig}를 생성한다.
     *
     * @param mcp MCP 엔티티
     * @return 프로세스 실행 설정
     * @throws IllegalArgumentException config에 command가 없는 경우
     */
    /**
     * @throws IllegalArgumentException config 형식이 잘못된 경우
     */
    @SuppressWarnings("unchecked")
    public static McpConfig from(Mcp mcp) {
        Map<String, Object> config = mcp.getConfig();
        String mcpName = mcp.getName();

        Object commandObj = config.get("command");
        if (!(commandObj instanceof String command) || command.isBlank()) {
            throw new IllegalArgumentException(
                    "MCP config에 유효한 command(String)가 설정되지 않았습니다: name=" + mcpName);
        }

        List<String> args = parseArgs(config.get("args"), mcpName);
        Map<String, String> env = parseEnv(config.get("env"), mcpName);

        return new McpConfig(mcpName, command, args, env);
    }

    private static List<String> parseArgs(Object argsObj, String mcpName) {
        if (argsObj == null) {
            return List.of();
        }
        if (!(argsObj instanceof List<?> argsList)) {
            throw new IllegalArgumentException(
                    "MCP config의 args는 배열이어야 합니다: name=" + mcpName);
        }
        return argsList.stream()
                .map(arg -> {
                    if (arg == null) {
                        throw new IllegalArgumentException(
                                "MCP config의 args에 null 요소가 포함되어 있습니다: name=" + mcpName);
                    }
                    return arg.toString();
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> parseEnv(Object envObj, String mcpName) {
        if (envObj == null) {
            return Map.of();
        }
        if (!(envObj instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(
                    "MCP config의 env는 객체(Map)여야 합니다: name=" + mcpName);
        }
        return toStringMap((Map<String, Object>) envObj);
    }

    /**
     * 프로세스 실행 명령어를 리스트로 반환한다.
     *
     * <p>command와 args를 결합하여 {@link ProcessBuilder}에 전달할 수 있는
     * 형태로 반환한다.</p>
     *
     * @return command + args 리스트
     */
    public List<String> buildCommandLine() {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(command);
        commandLine.addAll(args);
        return Collections.unmodifiableList(commandLine);
    }

    private static Map<String, String> toStringMap(Map<String, Object> source) {
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, value != null ? value.toString() : ""));
        return Collections.unmodifiableMap(result);
    }
}
