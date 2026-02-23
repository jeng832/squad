package com.squad.llm.claude;

import com.squad.llm.LlmProvider;
import com.squad.llm.model.*;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.concurrent.TimeoutException;

/**
 * Anthropic Claude Messages API 구현체.
 *
 * <p>이 클래스는 {@code ClaudeConfig}에서 {@code @Bean}으로 등록되어 사용되며,
 * {@code @Service}/{@code @Component} 어노테이션을 사용하지 않습니다.</p>
 */
public class ClaudeProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeProvider.class);
    private static final String PROVIDER_NAME = "claude";
    private static final String MESSAGES_PATH = "/v1/messages";
    private static final String DEFAULT_API_VERSION = "2023-06-01";

    private final WebClient webClient;
    private final String defaultModel;
    private final int defaultMaxTokens;
    private final Duration timeout;
    private final int maxRetries;
    private final long initialBackoffMillis;
    private final long maxBackoffMillis;
    private final double jitterRatio;

    public ClaudeProvider(
            WebClient webClient,
            String defaultModel,
            int defaultMaxTokens,
            long timeoutMillis,
            int maxRetries,
            long initialBackoffMillis,
            long maxBackoffMillis,
            double jitterRatio
    ) {
        this.webClient = webClient;
        this.defaultModel = defaultModel;
        this.defaultMaxTokens = defaultMaxTokens;
        this.timeout = Duration.ofMillis(timeoutMillis);
        this.maxRetries = Math.max(0, maxRetries);
        this.initialBackoffMillis = Math.max(0, initialBackoffMillis);
        this.maxBackoffMillis = Math.max(this.initialBackoffMillis, maxBackoffMillis);
        this.jitterRatio = Math.max(0.0, jitterRatio);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public LlmResponse sendMessage(LlmRequest request) {
        ClaudeRequest body = toClaudeRequest(request);
        try {
            String json = new ObjectMapper().writeValueAsString(body);
            log.debug("Claude 요청 JSON: {}", json);
        } catch (JsonProcessingException e) {
            log.warn("Claude 요청 직렬화 실패", e);
        }
        int maxAttempts = Math.max(1, maxRetries + 1);
        long backoffMillis = initialBackoffMillis;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                var requestSpec = webClient.post()
                        .uri(MESSAGES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("anthropic-version", DEFAULT_API_VERSION);
                if (request.apiKey() != null && !request.apiKey().isBlank()) {
                    requestSpec = requestSpec.header("x-api-key", request.apiKey());
                }
                Object result = requestSpec
                        .body(BodyInserters.fromValue(body))
                        .exchangeToMono(resp -> {
                            if (resp.statusCode().isError()) {
                                return resp.bodyToMono(String.class)
                                        .defaultIfEmpty("(응답 본문 없음)")
                                        .map(errorBody -> new IllegalStateException(
                                                "Claude 호출 실패: " + resp.statusCode() + " " + errorBody));
                            }
                            return resp.bodyToMono(ClaudeResponse.class);
                        })
                        .block(timeout);

                if (result == null) {
                    if (attempt < maxAttempts) {
                        backoffMillis = sleepBackoff(backoffMillis);
                        continue;
                    }
                    throw new IllegalStateException("Claude 응답이 없습니다.");
                }

                if (result instanceof IllegalStateException error) {
                    if (attempt < maxAttempts && isRetryableErrorMessage(error.getMessage())) {
                        backoffMillis = sleepBackoff(backoffMillis);
                        continue;
                    }
                    throw error;
                }

                ClaudeResponse claudeResponse = (ClaudeResponse) result;
                if (claudeResponse == null) {
                    if (attempt < maxAttempts) {
                        backoffMillis = sleepBackoff(backoffMillis);
                        continue;
                    }
                    throw new IllegalStateException("Claude 응답 파싱 실패");
                }

                return toLlmResponse(claudeResponse);
            } catch (RuntimeException ex) {
                if (attempt < maxAttempts && isRetryableException(ex)) {
                    backoffMillis = sleepBackoff(backoffMillis);
                    continue;
                }
                throw ex;
            }
        }

        throw new IllegalStateException("Claude 호출 재시도 실패");
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

    private boolean isRetryableErrorMessage(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("429 TOO_MANY_REQUESTS") || message.contains("5") && message.contains("SERVER_ERROR");
    }

    private boolean isRetryableException(RuntimeException ex) {
        if (ex instanceof WebClientRequestException) {
            return true;
        }
        Throwable cause = ex.getCause();
        return cause instanceof TimeoutException;
    }

    private long sleepBackoff(long currentBackoffMillis) {
        long delayMillis = currentBackoffMillis;
        if (delayMillis > 0) {
            long jitter = (long) Math.floor(delayMillis * jitterRatio);
            long minDelay = Math.max(0, delayMillis - jitter);
            long maxDelay = delayMillis + jitter;
            if (maxDelay > minDelay) {
                delayMillis = ThreadLocalRandom.current().nextLong(minDelay, maxDelay + 1);
            }
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        if (currentBackoffMillis <= 0) {
            return currentBackoffMillis;
        }
        long nextBackoff = currentBackoffMillis * 2;
        return Math.min(maxBackoffMillis, nextBackoff);
    }

}
