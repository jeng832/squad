package com.squad.mcp.process;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 실행 중인 MCP 서버 프로세스와의 연결을 나타내는 클래스.
 *
 * <p>MCP 서버 프로세스의 stdin/stdout 스트림을 래핑하여
 * JSON-RPC 메시지를 주고받을 수 있는 통신 채널을 제공한다.</p>
 *
 * <p>연결 종료 시 스트림과 프로세스를 안전하게 정리한다.</p>
 *
 * @see McpProcessManager
 * @see McpConfig
 */
@Slf4j
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class McpConnection {

    private final String name;
    private final Process process;
    private final BufferedWriter stdin;
    private final BufferedReader stdout;
    private final BufferedReader stderr;

    /**
     * 실행 중인 프로세스로부터 {@code McpConnection}을 생성한다.
     *
     * @param name    MCP 이름
     * @param process 실행 중인 프로세스
     * @return MCP 연결
     */
    public static McpConnection of(String name, Process process) {
        BufferedWriter stdin = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader stdout = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        BufferedReader stderr = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));

        return new McpConnection(name, process, stdin, stdout, stderr);
    }

    /**
     * 프로세스가 살아있는지 확인한다.
     *
     * @return 프로세스 실행 중이면 {@code true}
     */
    public boolean isAlive() {
        return process.isAlive();
    }

    /**
     * MCP 연결을 종료한다.
     *
     * <p>stdin/stdout/stderr 스트림을 닫고, 프로세스가 살아있으면
     * 정상 종료를 시도한다. 정상 종료 실패 시 강제 종료한다.</p>
     */
    public void close() {
        closeStream(stdin, "stdin");
        closeStream(stdout, "stdout");
        closeStream(stderr, "stderr");

        if (process.isAlive()) {
            process.destroy();
            log.debug("MCP 프로세스 종료 요청: name={}", name);

            try {
                boolean exited = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                if (!exited) {
                    process.destroyForcibly();
                    log.warn("MCP 프로세스 강제 종료: name={}", name);
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                log.warn("MCP 프로세스 종료 대기 중 인터럽트: name={}", name);
            }
        }
    }

    private void closeStream(Closeable stream, String streamName) {
        try {
            stream.close();
        } catch (IOException e) {
            log.warn("MCP {} 스트림 닫기 실패: name={}", streamName, name, e);
        }
    }
}
