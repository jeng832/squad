package com.squad.llm.claude;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.squad.llm.LlmProvider;
import com.squad.llm.model.LlmMessage;
import com.squad.llm.model.LlmRequest;
import com.squad.llm.model.LlmResponse;
import com.squad.llm.model.LlmTool;
import com.squad.llm.model.LlmToolCall;
import com.squad.llm.model.LlmUsage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Anthropic Claude Messages API 구현체.
 */
public class ClaudeProvider implements LlmProvider {

    private static final String PROVIDER_NAME = "claude";
    private static final String MESSAGES_PATH = "/v1/messages";
    private static final String DEFAULT_API_VERSION = "2023-06-01";

    private final WebClient webClient;
    private final String defaultModel;
    private final int defaultMaxTokens;
    private final Duration timeout;
    public ClaudeProvider(WebClient webClient, String defaultModel, int defaultMaxTokens, long timeoutMillis) {
        this.webClient = webClient;
        this.defaultModel = defaultModel;
        this.defaultMaxTokens = defaultMaxTokens;
        this.timeout = Duration.ofMillis(timeoutMillis);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public LlmResponse sendMessage(LlmRequest request) {
        ClaudeRequest body = toClaudeRequest(request);

        ClientResponse response = webClient.post()
                .uri(MESSAGES_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header("anthropic-version", DEFAULT_API_VERSION)
                .body(BodyInserters.fromValue(body))
                .exchangeToMono(Mono::just)
                .block(timeout);

        if (response == null) {
            throw new IllegalStateException("Claude 응답이 없습니다.");
        }
        if (response.statusCode().isError()) {
            String errorBody = response.bodyToMono(String.class).block(timeout);
            throw new IllegalStateException("Claude 호출 실패: " + response.statusCode() + " " + errorBody);
        }

        ClaudeResponse claudeResponse = response.bodyToMono(ClaudeResponse.class).block(timeout);
        if (claudeResponse == null) {
            throw new IllegalStateException("Claude 응답 파싱 실패");
        }

        return toLlmResponse(claudeResponse);
    }

    private ClaudeRequest toClaudeRequest(LlmRequest request) {
        List<ClaudeMessage> messages = new ArrayList<>();
        if (request.messages() != null) {
            for (LlmMessage msg : request.messages()) {
                messages.add(new ClaudeMessage(
                        msg.role(),
                        Collections.singletonList(new ClaudeContent("text", msg.content(), null, null, null))
                ));
            }
        }

        List<ClaudeTool> tools = null;
        if (request.tools() != null && !request.tools().isEmpty()) {
            tools = request.tools().stream()
                    .map(this::toClaudeTool)
                    .toList();
        }

        Integer maxTokens = request.maxTokens() != null ? request.maxTokens() : defaultMaxTokens;
        String model = request.model() != null ? request.model() : defaultModel;

        return new ClaudeRequest(model, maxTokens, request.temperature(), request.systemPrompt(), messages, tools);
    }

    private ClaudeTool toClaudeTool(LlmTool tool) {
        return new ClaudeTool(tool.name(), tool.description(), tool.inputSchema());
    }

    private LlmResponse toLlmResponse(ClaudeResponse response) {
        String content = response.content().stream()
                .filter(block -> Objects.equals(block.type(), "text"))
                .map(ClaudeContent::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining());

        List<LlmToolCall> toolCalls = response.content().stream()
                .filter(block -> Objects.equals(block.type(), "tool_use") && block.toolUseName() != null)
                .map(block -> new LlmToolCall(
                        block.toolUseId(),
                        block.toolUseName(),
                        block.toolUseInput()
                ))
                .toList();

        LlmUsage usage = null;
        if (response.usage() != null) {
            usage = new LlmUsage(response.usage().inputTokens(), response.usage().outputTokens());
        }

        return new LlmResponse(
                response.id(),
                content,
                response.stopReason(),
                toolCalls,
                usage
        );
    }

    // === Claude API DTOs ===
    record ClaudeRequest(
            String model,
            @JsonProperty("max_tokens") Integer maxTokens,
            Double temperature,
            @JsonProperty("system") String systemPrompt,
            List<ClaudeMessage> messages,
            List<ClaudeTool> tools
    ) {
    }

    record ClaudeMessage(
            String role,
            List<ClaudeContent> content
    ) {
    }

    record ClaudeContent(
            String type,
            String text,
            @JsonProperty("id") String toolUseId,
            @JsonProperty("name") String toolUseName,
            @JsonProperty("input") Map<String, Object> toolUseInput
    ) {
    }

    record ClaudeTool(
            String name,
            String description,
            @JsonProperty("input_schema") Map<String, Object> inputSchema
    ) {
    }


    record ClaudeUsage(
            @JsonProperty("input_tokens") Integer inputTokens,
            @JsonProperty("output_tokens") Integer outputTokens
    ) {
    }

    record ClaudeResponse(
            String id,
            List<ClaudeContent> content,
            @JsonProperty("stop_reason") String stopReason,
            ClaudeUsage usage
    ) {
    }
}
