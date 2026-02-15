package com.squad.mcp.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squad.llm.model.LlmTool;
import com.squad.mcp.client.McpClient;
import com.squad.mcp.client.McpToolInfo;
import com.squad.mcp.process.EnvResolver;
import com.squad.mcp.process.McpConfig;
import com.squad.mcp.process.McpConnection;
import com.squad.mcp.process.McpProcessManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.Objects;

/**
 * MCP 서버의 도구 목록을 관리하는 레지스트리.
 *
 * <p>각 MCP 서버에 연결하여 {@code tools/list}로 도구 목록을 조회하고,
 * LLM에 전달할 수 있는 {@link LlmTool} 형식으로 변환한다.
 * 도구 이름 충돌을 방지하기 위해 {@code mcpName__toolName} alias를 사용하며,
 * alias에서 원본 MCP/도구 이름으로의 라우팅 매핑을 관리한다.</p>
 *
 * <h3>동시성 모델:</h3>
 * <p>모든 public 메서드는 {@code synchronized}로 보호되어 단일 락 기반으로
 * 일관성을 보장한다. 여러 내부 Map에 대한 복합 연산의 원자성이 필요하므로
 * lock-free 자료구조 대신 단일 락을 사용한다.</p>
 *
 * @see ToolRoute
 * @see McpProcessManager
 * @see McpClient
 */
@Slf4j
@Service
public class McpToolRegistry {

    private final McpProcessManager processManager;
    private final EnvResolver envResolver;
    private final ObjectMapper objectMapper;

    private final Map<String, McpClient> clients = new HashMap<>();
    private final Map<String, List<McpToolInfo>> toolCache = new HashMap<>();
    private final Map<String, ToolRoute> routeMap = new HashMap<>();

    /**
     * {@code McpToolRegistry}를 생성한다.
     *
     * @param processManager MCP 프로세스 관리자
     * @param envResolver    환경변수 Secret 참조 해결기
     * @param objectMapper   JSON 직렬화/역직렬화 매퍼
     */
    public McpToolRegistry(McpProcessManager processManager, EnvResolver envResolver, ObjectMapper objectMapper) {
        this.processManager = processManager;
        this.envResolver = envResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * MCP 서버를 시작하고 도구 목록을 등록한다.
     *
     * <p>프로세스 시작 → initialize 핸드셰이크 → tools/list 조회 순서로
     * 진행된다. 이미 등록된 MCP는 기존 캐시를 무효화한 후 재등록한다.</p>
     *
     * <p>초기화 또는 도구 조회 중 실패하면 시작된 프로세스를 정리하고
     * 예외를 전파한다.</p>
     *
     * @param config MCP 프로세스 설정
     * @return 등록된 도구 목록 (LLM 전달용)
     */
    public synchronized List<LlmTool> registerMcp(McpConfig config) {
        Objects.requireNonNull(config, "config는 null일 수 없습니다");
        String mcpName = config.getName();

        Map<String, String> resolvedEnv = envResolver.resolve(config.getEnv());
        McpConfig resolvedConfig = config.withResolvedEnv(resolvedEnv);

        clearMcpState(mcpName);
        McpConnection connection = processManager.start(resolvedConfig);
        try {
            McpClient client = new McpClient(connection, objectMapper);
            client.initialize();

            List<McpToolInfo> tools = client.listTools();
            clients.put(mcpName, client);
            toolCache.put(mcpName, List.copyOf(tools));

            for (McpToolInfo tool : tools) {
                ToolRoute route = ToolRoute.of(mcpName, tool.name());
                routeMap.put(route.alias(), route);
            }

            log.info("MCP 도구 등록 완료: mcp={}, 도구 수={}", mcpName, tools.size());
            return toLlmTools(mcpName, tools);
        } catch (Exception e) {
            try {
                processManager.stop(mcpName);
            } catch (Exception stopEx) {
                e.addSuppressed(stopEx);
            }
            throw e;
        }
    }

    /**
     * 특정 MCP의 도구 목록을 LLM 전달용으로 조회한다.
     *
     * <p>캐시된 도구 목록을 반환한다. 등록되지 않은 MCP이면 빈 리스트를 반환한다.</p>
     *
     * @param mcpName MCP 이름
     * @return LLM 도구 목록
     */
    public synchronized List<LlmTool> getTools(String mcpName) {
        List<McpToolInfo> tools = toolCache.get(mcpName);
        if (tools == null) {
            return List.of();
        }
        return toLlmTools(mcpName, tools);
    }

    /**
     * 등록된 모든 MCP의 도구 목록을 LLM 전달용으로 조회한다.
     *
     * @return 전체 LLM 도구 목록
     */
    public synchronized List<LlmTool> getAllTools() {
        List<LlmTool> allTools = new ArrayList<>();
        toolCache.forEach((mcpName, tools) -> allTools.addAll(toLlmTools(mcpName, tools)));
        return Collections.unmodifiableList(allTools);
    }

    /**
     * 지정된 MCP 이름 목록의 도구를 LLM 전달용으로 조회한다.
     *
     * <p>등록되지 않은 MCP는 건너뛴다.</p>
     *
     * @param mcpNames MCP 이름 목록
     * @return LLM 도구 목록
     */
    public synchronized List<LlmTool> getToolsForMcps(List<String> mcpNames) {
        List<LlmTool> tools = new ArrayList<>();
        for (String mcpName : mcpNames) {
            List<McpToolInfo> cached = toolCache.get(mcpName);
            if (cached != null) {
                tools.addAll(toLlmTools(mcpName, cached));
            }
        }
        return Collections.unmodifiableList(tools);
    }

    /**
     * alias로 도구 라우팅 정보를 조회한다.
     *
     * <p>9-4 (MCP Tool 실행 통합)에서 LLM의 tool_use 응답을 올바른
     * MCP 서버로 라우팅할 때 사용한다.</p>
     *
     * @param alias 도구 alias ({@code mcpName__toolName} 형식)
     * @return 라우팅 정보 (없으면 empty)
     */
    public synchronized Optional<ToolRoute> findRoute(String alias) {
        return Optional.ofNullable(routeMap.get(alias));
    }

    /**
     * 특정 MCP의 등록을 해제하고 프로세스를 종료한다.
     *
     * @param mcpName MCP 이름
     */
    public synchronized void unregisterMcp(String mcpName) {
        clearMcpState(mcpName);
        processManager.stop(mcpName);
        log.info("MCP 도구 등록 해제: mcp={}", mcpName);
    }

    /**
     * 모든 MCP 등록을 해제하고 프로세스를 종료한다.
     */
    public synchronized void unregisterAll() {
        Set<String> names = Set.copyOf(clients.keySet());
        clients.clear();
        toolCache.clear();
        routeMap.clear();
        names.forEach(processManager::stop);
        log.info("모든 MCP 도구 등록 해제");
    }

    /**
     * 특정 MCP의 {@link McpClient}를 반환한다.
     *
     * <p>9-4 (MCP Tool 실행 통합)에서 도구 호출에 사용한다.</p>
     *
     * @param mcpName MCP 이름
     * @return McpClient (없으면 empty)
     */
    public synchronized Optional<McpClient> getClient(String mcpName) {
        return Optional.ofNullable(clients.get(mcpName));
    }

    /**
     * 등록된 MCP 이름 목록을 반환한다.
     *
     * @return 등록된 MCP 이름 Set (스냅샷)
     */
    public synchronized Set<String> getRegisteredMcpNames() {
        return Set.copyOf(toolCache.keySet());
    }

    private void clearMcpState(String mcpName) {
        clients.remove(mcpName);
        List<McpToolInfo> removed = toolCache.remove(mcpName);
        if (removed != null) {
            for (McpToolInfo tool : removed) {
                routeMap.remove(ToolRoute.aliasOf(mcpName, tool.name()));
            }
        }
    }

    private List<LlmTool> toLlmTools(String mcpName, List<McpToolInfo> tools) {
        return tools.stream()
                .map(tool -> {
                    String alias = ToolRoute.aliasOf(mcpName, tool.name());
                    Map<String, Object> schema = objectMapper.convertValue(
                            tool.inputSchema(),
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    return new LlmTool(alias, tool.description(), schema);
                })
                .toList();
    }
}
