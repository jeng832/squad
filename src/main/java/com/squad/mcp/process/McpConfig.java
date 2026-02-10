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
    @SuppressWarnings("unchecked")
    public static McpConfig from(Mcp mcp) {
        Map<String, Object> config = mcp.getConfig();

        String command = (String) config.get("command");
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException(
                    "MCP config에 command가 설정되지 않았습니다: name=" + mcp.getName());
        }

        List<String> args = config.containsKey("args")
                ? ((List<Object>) config.get("args")).stream()
                        .map(Object::toString)
                        .toList()
                : List.of();

        Map<String, String> env = config.containsKey("env")
                ? toStringMap((Map<String, Object>) config.get("env"))
                : Map.of();

        return new McpConfig(mcp.getName(), command, args, env);
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
