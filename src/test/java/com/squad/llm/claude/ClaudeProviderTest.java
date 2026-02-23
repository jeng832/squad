package com.squad.llm.claude;

import com.squad.llm.model.LlmMessage;
import com.squad.llm.model.LlmRequest;
import com.squad.llm.model.LlmResponse;
import com.squad.llm.model.LlmTool;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeProviderTest {

    @Test
    void Claude_응답을_LlmResponse로_매핑() {
        String jsonResponse = """
                {
                  "id": "msg_123",
                  "content": [
                    {"type": "text", "text": "hello"},
                    {"type": "tool_use", "id": "call_1", "name": "do_something", "input": {"x": 1}}
                  ],
                  "stop_reason": "end_turn",
                  "usage": {"input_tokens": 10, "output_tokens": 20}
                }
                """;
        ExchangeFunction exchange = req -> Mono.just(
                ClientResponse.create(HttpStatus.OK, ExchangeStrategies.withDefaults())
                        .header("Content-Type", "application/json")
                        .body(jsonResponse)
                        .build()
        );
        WebClient client = WebClient.builder()
                .exchangeFunction(exchange)
                .baseUrl("https://api.anthropic.com")
                .build();

        ClaudeProvider provider = new ClaudeProvider(client, "claude-sonnet-4-20250514", 1024, 1000, 0, 0, 0, 0.0);

        LlmRequest request = new LlmRequest(
                null,
                "sys",
                List.of(new LlmMessage("user", "hi")),
                null,
                null,
                List.of(new LlmTool("tool", "desc", Map.of("type", "object"))),
                null
        );

        LlmResponse response = provider.sendMessage(request);

        assertThat(response.id()).isEqualTo("msg_123");
        assertThat(response.content()).isEqualTo("hello");
        assertThat(response.finishReason()).isEqualTo("end_turn");
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().get(0).name()).isEqualTo("do_something");
        assertThat(response.usage().inputTokens()).isEqualTo(10);
    }

    @Test
    void Http429_응답_후_재시도하여_성공() {
        String okResponse = """
                {
                  "id": "msg_456",
                  "content": [
                    {"type": "text", "text": "ok"}
                  ],
                  "stop_reason": "end_turn",
                  "usage": {"input_tokens": 5, "output_tokens": 6}
                }
                """;
        AtomicInteger calls = new AtomicInteger();
        ExchangeFunction exchange = req -> {
            if (calls.getAndIncrement() == 0) {
                return Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS, ExchangeStrategies.withDefaults())
                        .header("Content-Type", "application/json")
                        .body("{\"error\":\"rate limit\"}")
                        .build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK, ExchangeStrategies.withDefaults())
                    .header("Content-Type", "application/json")
                    .body(okResponse)
                    .build());
        };
        WebClient client = WebClient.builder()
                .exchangeFunction(exchange)
                .baseUrl("https://api.anthropic.com")
                .build();

        ClaudeProvider provider = new ClaudeProvider(client, "claude-sonnet-4-20250514", 1024, 1000, 1, 1, 1, 0.0);

        LlmRequest request = new LlmRequest(
                null,
                "sys",
                List.of(new LlmMessage("user", "hi")),
                null,
                null,
                List.of(new LlmTool("tool", "desc", Map.of("type", "object"))),
                null
        );

        LlmResponse response = provider.sendMessage(request);

        assertThat(response.id()).isEqualTo("msg_456");
        assertThat(calls.get()).isEqualTo(2);
    }
}
