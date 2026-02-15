package com.squad.mcp.gateway;

import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import com.squad.llm.model.LlmTool;
import com.squad.llm.model.LlmToolCall;
import com.squad.llm.tool.LlmToolResult;
import com.squad.mcp.domain.Mcp;
import com.squad.mcp.gateway.dto.McpGatewayEvent;
import com.squad.mcp.process.McpConfig;
import com.squad.mcp.repository.McpRepository;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP 서버를 중앙 관리하고 Agent에 HTTP/SSE 엔드포인트를 제공하는 서비스.
 */
@Service
public class McpGatewayService {

    private final McpRepository mcpRepository;
    private final McpToolRegistry mcpToolRegistry;
    private final McpToolExecutor mcpToolExecutor;
    private final Sinks.Many<McpGatewayEvent> eventSink = Sinks.many().multicast().onBackpressureBuffer();

    public McpGatewayService(
            McpRepository mcpRepository,
            McpToolRegistry mcpToolRegistry,
            McpToolExecutor mcpToolExecutor
    ) {
        this.mcpRepository = mcpRepository;
        this.mcpToolRegistry = mcpToolRegistry;
        this.mcpToolExecutor = mcpToolExecutor;
    }

    public List<LlmTool> register(String mcpName) {
        Mcp mcp = mcpRepository.findByName(mcpName)
                .orElseThrow(() -> new NotFoundException(ErrorCode.MCP_NOT_FOUND));

        List<LlmTool> tools = mcpToolRegistry.registerMcp(McpConfig.from(mcp));
        publish("MCP_REGISTERED", mcpName, null, "MCP가 등록되었습니다.");
        return tools;
    }

    public void unregister(String mcpName) {
        mcpToolRegistry.unregisterMcp(mcpName);
        publish("MCP_UNREGISTERED", mcpName, null, "MCP가 해제되었습니다.");
    }

    public List<LlmTool> getTools(String mcpName) {
        if (mcpName == null || mcpName.isBlank()) {
            return mcpToolRegistry.getAllTools();
        }
        return mcpToolRegistry.getTools(mcpName);
    }

    public LlmToolResult callTool(String toolAlias, Map<String, Object> arguments) {
        LlmToolCall toolCall = new LlmToolCall(
                UUID.randomUUID().toString(),
                toolAlias,
                arguments == null ? Map.of() : arguments
        );
        LlmToolResult result = mcpToolExecutor.execute(toolCall);
        publish("TOOL_CALLED", null, toolAlias, "도구 호출이 수행되었습니다.");
        return result;
    }

    public Flux<ServerSentEvent<McpGatewayEvent>> streamEvents() {
        return eventSink.asFlux()
                .map(event -> ServerSentEvent.<McpGatewayEvent>builder()
                        .event(event.type())
                        .data(event)
                        .build());
    }

    private void publish(String type, String mcpName, String toolAlias, String message) {
        eventSink.tryEmitNext(new McpGatewayEvent(type, mcpName, toolAlias, message, LocalDateTime.now()));
    }
}
