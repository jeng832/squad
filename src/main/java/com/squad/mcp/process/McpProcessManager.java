package com.squad.mcp.process;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 서버 프로세스의 lifecycle을 관리하는 서비스.
 *
 * <p>MCP 서버를 subprocess로 시작하고, stdin/stdout 기반 통신 채널을
 * 설정하며, 종료 시 프로세스를 정리한다.</p>
 *
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li>MCP 서버 프로세스 시작 ({@link #start(McpConfig)})</li>
 *   <li>특정 MCP 연결 종료 ({@link #stop(String)})</li>
 *   <li>모든 MCP 연결 종료 ({@link #stopAll()})</li>
 *   <li>활성 연결 조회 ({@link #getConnection(String)})</li>
 * </ul>
 *
 * <p>연결은 MCP 이름으로 관리되며, 동일 이름의 MCP를 중복 시작하면
 * 기존 연결을 먼저 종료한 후 새로 시작한다.</p>
 *
 * @see McpConfig
 * @see McpConnection
 */
@Slf4j
@Service
public class McpProcessManager {

    private final Map<String, McpConnection> connections = new ConcurrentHashMap<>();

    /**
     * MCP 서버 프로세스를 시작하고 연결을 반환한다.
     *
     * <p>동일 이름의 MCP가 이미 실행 중이면 기존 연결을 종료한 후
     * 새로 시작한다.</p>
     *
     * @param config MCP 프로세스 설정
     * @return MCP 연결
     * @throws McpProcessException 프로세스 시작에 실패한 경우
     */
    public McpConnection start(McpConfig config) {
        stop(config.getName());

        try {
            Process process = buildProcess(config);
            McpConnection connection = McpConnection.of(config.getName(), process);
            connections.put(config.getName(), connection);

            log.info("MCP 프로세스 시작: name={}, command={}",
                    config.getName(), config.buildCommandLine());

            return connection;
        } catch (IOException e) {
            throw new McpProcessException(
                    "MCP 프로세스 시작 실패: name=" + config.getName(), e);
        }
    }

    /**
     * 특정 MCP 연결을 종료한다.
     *
     * <p>해당 이름의 연결이 없으면 아무 작업도 수행하지 않는다.</p>
     *
     * @param name MCP 이름
     */
    public void stop(String name) {
        McpConnection connection = connections.remove(name);
        if (connection != null) {
            connection.close();
            log.info("MCP 프로세스 종료: name={}", name);
        }
    }

    /**
     * 모든 MCP 연결을 종료한다.
     */
    public void stopAll() {
        connections.forEach((name, connection) -> {
            connection.close();
            log.info("MCP 프로세스 종료: name={}", name);
        });
        connections.clear();
    }

    /**
     * 활성 MCP 연결을 조회한다.
     *
     * @param name MCP 이름
     * @return MCP 연결 (없으면 empty)
     */
    public Optional<McpConnection> getConnection(String name) {
        return Optional.ofNullable(connections.get(name));
    }

    /**
     * 현재 활성 연결 수를 반환한다.
     *
     * @return 활성 연결 수
     */
    public int getActiveConnectionCount() {
        return connections.size();
    }

    private Process buildProcess(McpConfig config) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(config.buildCommandLine());
        processBuilder.environment().putAll(config.getEnv());
        processBuilder.redirectErrorStream(false);

        return processBuilder.start();
    }
}
