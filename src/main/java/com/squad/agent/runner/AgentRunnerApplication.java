package com.squad.agent.runner;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

/**
 * Agent Container 내부에서 실행되는 Runner 진입점.
 */
public class AgentRunnerApplication {

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws Exception {
        String agentId = System.getenv("AGENT_ID");
        int port = parsePort(System.getenv("PORT"));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));

        System.out.println("[agent-runner] started id=" + agentId + ", port=" + port);
        new CountDownLatch(1).await();
    }

    private static int parsePort(String rawPort) {
        if (rawPort == null || rawPort.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(rawPort);
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }
}
