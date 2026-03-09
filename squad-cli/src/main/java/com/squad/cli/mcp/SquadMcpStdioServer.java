package com.squad.cli.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * MCP JSON-RPC 메시지를 stdio로 처리하는 서버.
 */
@Component
public class SquadMcpStdioServer {

    private static final Logger log = LoggerFactory.getLogger(SquadMcpStdioServer.class);

    private final SquadMcpRequestHandler requestHandler;
    private final ObjectMapper objectMapper;

    public SquadMcpStdioServer(SquadMcpRequestHandler requestHandler, ObjectMapper objectMapper) {
        this.requestHandler = requestHandler;
        this.objectMapper = objectMapper;
    }

    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                JsonNode response = requestHandler.handleLine(line);
                if (response == null) {
                    continue;
                }

                writer.write(objectMapper.writeValueAsString(response));
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            log.error("MCP stdio 서버 실행 중 오류", e);
        }
    }
}
