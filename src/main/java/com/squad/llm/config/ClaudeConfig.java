package com.squad.llm.config;

import com.squad.llm.claude.ClaudeProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class ClaudeConfig {

    @Bean
    public ClaudeProvider claudeProvider(
            WebClient.Builder builder,
            @Value("${squad.llm.claude.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${squad.llm.claude.api-key:}") String apiKey,
            @Value("${squad.llm.claude.default-model:claude-sonnet-4-20250514}") String defaultModel,
            @Value("${squad.llm.claude.max-tokens:4096}") int defaultMaxTokens,
            @Value("${squad.llm.claude.timeout:60000}") long timeoutMillis,
            @Value("${squad.llm.claude.retry.max-retries:2}") int maxRetries,
            @Value("${squad.llm.claude.retry.initial-backoff:200}") long initialBackoffMillis,
            @Value("${squad.llm.claude.retry.max-backoff:2000}") long maxBackoffMillis,
            @Value("${squad.llm.claude.retry.jitter:0.2}") double jitterRatio
    ) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(timeoutMillis));

        WebClient webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        return new ClaudeProvider(
                webClient,
                defaultModel,
                defaultMaxTokens,
                timeoutMillis,
                maxRetries,
                initialBackoffMillis,
                maxBackoffMillis,
                jitterRatio
        );
    }
}
