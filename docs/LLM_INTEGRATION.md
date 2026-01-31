# LLM 연동 가이드 및 API 조사

## 1. 개요

이 문서는 Squad 플랫폼에서 지원할 LLM Provider들의 API를 조사하고, MVP 개발을 위한 우선순위를 결정합니다.

---

## 2. LLM Provider 비교

| Provider | 주요 모델 | 강점 | API 특징 | 가격대 |
|----------|----------|------|----------|--------|
| **Anthropic Claude** | Claude Opus 4.5, Claude Sonnet 4 | 긴 컨텍스트, 코딩 능력, 안전성 | Messages API, Stateless | 중상 |
| **OpenAI** | GPT-5.2, GPT-4.1 | 범용성, 풍부한 생태계 | Chat Completions API | 중상 |
| **Google Gemini** | Gemini 3 Pro, Gemini 3 Flash | 멀티모달, 빠른 속도 | Interactions API | 중 |

---

## 3. Anthropic Claude API

### 3.1 API 개요

- **Base URL**: `https://api.anthropic.com`
- **주요 엔드포인트**: `POST /v1/messages`
- **인증**: `x-api-key` 헤더
- **특징**: Stateless API (전체 대화 히스토리를 매 요청에 전송)

### 3.2 주요 모델

| 모델 | 용도 | 컨텍스트 |
|------|------|----------|
| claude-opus-4-5-20251101 | 최고 성능, 복잡한 추론 | 200K tokens |
| claude-sonnet-4-20250514 | 균형잡힌 성능/비용 | 200K tokens |
| claude-haiku-3-5-20241022 | 빠른 응답, 저비용 | 200K tokens |

### 3.3 API 호출 예시

#### 기본 요청
```bash
curl https://api.anthropic.com/v1/messages \
  -H "Content-Type: application/json" \
  -H "x-api-key: $ANTHROPIC_API_KEY" \
  -H "anthropic-version: 2023-06-01" \
  -d '{
    "model": "claude-sonnet-4-20250514",
    "max_tokens": 1024,
    "messages": [
      {"role": "user", "content": "Hello, Claude"}
    ]
  }'
```

#### Java (Spring RestTemplate) 예시
```java
@Service
public class ClaudeApiClient {

    private final RestTemplate restTemplate;
    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    public ClaudeApiClient(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public ClaudeResponse sendMessage(String apiKey, ClaudeRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        HttpEntity<ClaudeRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ClaudeResponse> response = restTemplate.exchange(
            API_URL,
            HttpMethod.POST,
            entity,
            ClaudeResponse.class
        );

        return response.getBody();
    }
}

// Request DTO
@Data
public class ClaudeRequest {
    private String model;
    private int maxTokens;
    private List<Message> messages;
    private String system; // Optional system prompt

    @Data
    public static class Message {
        private String role; // "user" or "assistant"
        private String content;
    }
}

// Response DTO
@Data
public class ClaudeResponse {
    private String id;
    private String type;
    private String role;
    private List<Content> content;
    private String model;
    private String stopReason;
    private Usage usage;

    @Data
    public static class Content {
        private String type;
        private String text;
    }

    @Data
    public static class Usage {
        private int inputTokens;
        private int outputTokens;
    }
}
```

#### Tool Use (Function Calling) 예시
```json
{
  "model": "claude-sonnet-4-20250514",
  "max_tokens": 1024,
  "tools": [
    {
      "name": "get_weather",
      "description": "Get the current weather in a given location",
      "input_schema": {
        "type": "object",
        "properties": {
          "location": {
            "type": "string",
            "description": "The city and state"
          }
        },
        "required": ["location"]
      }
    }
  ],
  "messages": [
    {"role": "user", "content": "What's the weather in Seoul?"}
  ]
}
```

### 3.4 참고 문서

- [Messages API Reference](https://docs.anthropic.com/en/api/messages)
- [Getting Started](https://docs.anthropic.com/en/docs/get-started)
- [API Overview](https://docs.anthropic.com/)

---

## 4. OpenAI API

### 4.1 API 개요

- **Base URL**: `https://api.openai.com/v1`
- **주요 엔드포인트**: `POST /chat/completions`
- **인증**: `Authorization: Bearer` 헤더
- **특징**: 다양한 모델, Function Calling 지원

### 4.2 주요 모델

| 모델 | 용도 | 컨텍스트 |
|------|------|----------|
| gpt-5.2 | 최신 플래그십 모델 | 128K tokens |
| gpt-4.1 | 균형잡힌 성능 | 128K tokens |
| gpt-4.1-mini | 빠른 응답, 저비용 | 128K tokens |
| o3-pro | 고급 추론 | 128K tokens |

### 4.3 API 호출 예시

#### 기본 요청
```bash
curl https://api.openai.com/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -d '{
    "model": "gpt-4.1",
    "messages": [
      {"role": "system", "content": "You are a helpful assistant."},
      {"role": "user", "content": "Hello!"}
    ]
  }'
```

#### Java (Spring WebClient) 예시
```java
@Service
public class OpenAiApiClient {

    private final WebClient webClient;

    public OpenAiApiClient(WebClient.Builder builder) {
        this.webClient = builder
            .baseUrl("https://api.openai.com/v1")
            .build();
    }

    public Mono<OpenAiResponse> sendMessage(String apiKey, OpenAiRequest request) {
        return webClient.post()
            .uri("/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(OpenAiResponse.class);
    }
}

// Request DTO
@Data
public class OpenAiRequest {
    private String model;
    private List<Message> messages;
    private Double temperature;
    private Integer maxTokens;

    @Data
    public static class Message {
        private String role; // "system", "user", "assistant"
        private String content;
    }
}

// Response DTO
@Data
public class OpenAiResponse {
    private String id;
    private String object;
    private Long created;
    private String model;
    private List<Choice> choices;
    private Usage usage;

    @Data
    public static class Choice {
        private int index;
        private Message message;
        private String finishReason;
    }

    @Data
    public static class Usage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }
}
```

#### Function Calling 예시
```json
{
  "model": "gpt-4.1",
  "messages": [
    {"role": "user", "content": "What's the weather in Seoul?"}
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_weather",
        "description": "Get current weather",
        "parameters": {
          "type": "object",
          "properties": {
            "location": {"type": "string"}
          },
          "required": ["location"]
        }
      }
    }
  ]
}
```

### 4.4 참고 문서

- [Chat Completions API](https://platform.openai.com/docs/api-reference/chat)
- [Models](https://platform.openai.com/docs/models/)

---

## 5. Google Gemini API

### 5.1 API 개요

- **Base URL**: `https://generativelanguage.googleapis.com`
- **주요 엔드포인트**: `POST /v1beta/models/{model}:generateContent`
- **인증**: API Key 쿼리 파라미터 또는 OAuth
- **특징**: 멀티모달, MCP 네이티브 지원 (Interactions API)

### 5.2 주요 모델

| 모델 | 용도 | 컨텍스트 |
|------|------|----------|
| gemini-3-pro-preview | 최고 성능, 추론 | 2M tokens |
| gemini-3-flash-preview | 빠른 속도, 비용 효율 | 1M tokens |
| gemini-2.5-flash | 범용 | 1M tokens |

### 5.3 API 호출 예시

#### 기본 요청
```bash
curl "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=$GEMINI_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "contents": [
      {"role": "user", "parts": [{"text": "Hello!"}]}
    ]
  }'
```

#### Java 예시
```java
@Service
public class GeminiApiClient {

    private final WebClient webClient;
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    public GeminiApiClient(WebClient.Builder builder) {
        this.webClient = builder.baseUrl(BASE_URL).build();
    }

    public Mono<GeminiResponse> generateContent(String apiKey, String model, GeminiRequest request) {
        return webClient.post()
            .uri(uriBuilder -> uriBuilder
                .path("/models/{model}:generateContent")
                .queryParam("key", apiKey)
                .build(model))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(GeminiResponse.class);
    }
}

// Request DTO
@Data
public class GeminiRequest {
    private List<Content> contents;
    private GenerationConfig generationConfig;

    @Data
    public static class Content {
        private String role; // "user" or "model"
        private List<Part> parts;
    }

    @Data
    public static class Part {
        private String text;
    }

    @Data
    public static class GenerationConfig {
        private Double temperature;
        private Integer maxOutputTokens;
    }
}

// Response DTO
@Data
public class GeminiResponse {
    private List<Candidate> candidates;
    private UsageMetadata usageMetadata;

    @Data
    public static class Candidate {
        private Content content;
        private String finishReason;
    }

    @Data
    public static class UsageMetadata {
        private int promptTokenCount;
        private int candidatesTokenCount;
        private int totalTokenCount;
    }
}
```

#### Function Calling 예시
```json
{
  "contents": [
    {"role": "user", "parts": [{"text": "What's the weather in Seoul?"}]}
  ],
  "tools": [
    {
      "functionDeclarations": [
        {
          "name": "get_weather",
          "description": "Get current weather",
          "parameters": {
            "type": "object",
            "properties": {
              "location": {"type": "string"}
            },
            "required": ["location"]
          }
        }
      ]
    }
  ]
}
```

### 5.4 참고 문서

- [Gemini API Docs](https://ai.google.dev/gemini-api/docs)
- [Gemini Models](https://ai.google.dev/gemini-api/docs/models)
- [Interactions API](https://ai.google.dev/gemini-api/docs/interactions)

---

## 6. 공통 인터페이스 설계

### 6.1 LLM Provider 추상화

```java
public interface LlmProvider {

    /**
     * 메시지를 전송하고 응답을 받는다.
     */
    LlmResponse sendMessage(LlmRequest request);

    /**
     * 스트리밍 방식으로 응답을 받는다.
     */
    Flux<LlmStreamChunk> streamMessage(LlmRequest request);

    /**
     * Provider 이름을 반환한다.
     */
    String getProviderName();

    /**
     * 지원하는 모델 목록을 반환한다.
     */
    List<String> getSupportedModels();
}

// 공통 Request
@Data
@Builder
public class LlmRequest {
    private String model;
    private String systemPrompt;
    private List<LlmMessage> messages;
    private Integer maxTokens;
    private Double temperature;
    private List<Tool> tools;
}

// 공통 Response
@Data
@Builder
public class LlmResponse {
    private String id;
    private String content;
    private String finishReason;
    private List<ToolCall> toolCalls;
    private LlmUsage usage;
}

// 공통 Usage
@Data
@Builder
public class LlmUsage {
    private int inputTokens;
    private int outputTokens;
    private int totalTokens;
}
```

### 6.2 Provider 구현체

```java
@Component
public class ClaudeLlmProvider implements LlmProvider {
    // Claude API 구현
}

@Component
public class OpenAiLlmProvider implements LlmProvider {
    // OpenAI API 구현
}

@Component
public class GeminiLlmProvider implements LlmProvider {
    // Gemini API 구현
}
```

### 6.3 Provider Factory

```java
@Component
public class LlmProviderFactory {

    private final Map<String, LlmProvider> providers;

    public LlmProviderFactory(List<LlmProvider> providerList) {
        this.providers = providerList.stream()
            .collect(Collectors.toMap(
                LlmProvider::getProviderName,
                Function.identity()
            ));
    }

    public LlmProvider getProvider(String providerName) {
        LlmProvider provider = providers.get(providerName);
        if (provider == null) {
            throw new UnsupportedProviderException(providerName);
        }
        return provider;
    }
}
```

---

## 7. MVP LLM 선정

### 7.1 평가 기준

| 기준 | 가중치 | Claude | OpenAI | Gemini |
|------|--------|--------|--------|--------|
| API 안정성 | 25% | ★★★★★ | ★★★★★ | ★★★★☆ |
| 코딩 능력 | 25% | ★★★★★ | ★★★★☆ | ★★★★☆ |
| Tool Use 지원 | 20% | ★★★★★ | ★★★★★ | ★★★★☆ |
| 문서화 품질 | 15% | ★★★★★ | ★★★★★ | ★★★★☆ |
| 비용 효율성 | 15% | ★★★★☆ | ★★★★☆ | ★★★★★ |

### 7.2 MVP 결정: Anthropic Claude

**선정 이유:**

1. **코딩 특화 성능**
   - Squad의 초기 목표가 코딩 도메인이므로 코딩 능력이 뛰어난 Claude가 적합
   - Claude Code 등 코딩 도구로 검증된 성능

2. **간결한 API 구조**
   - Messages API가 단순하고 직관적
   - Stateless 방식으로 구현이 간단

3. **Tool Use 지원**
   - MCP 연동에 필요한 Tool Use가 잘 지원됨
   - Function Calling 안정성 높음

4. **긴 컨텍스트**
   - 200K 토큰으로 여러 레포 분석에 적합
   - 대화 히스토리 관리 용이

### 7.3 MVP 구현 모델

| 용도 | 모델 | 이유 |
|------|------|------|
| Orchestrator | claude-sonnet-4-20250514 | 균형잡힌 성능/비용, 빠른 판단 필요 |
| Worker | claude-sonnet-4-20250514 | 코딩 작업에 적합 |
| Analyst | claude-sonnet-4-20250514 | 분석 작업에 충분 |
| Scribe | claude-haiku-3-5-20241022 | 문서화는 저비용 모델로 충분 |

### 7.4 Phase 2 확장 계획

MVP 이후 다음 순서로 Provider 추가:

1. **Phase 2**: OpenAI GPT-4.1
   - 사용자 선택권 확대
   - 특정 작업에서 더 나은 성능 가능

2. **Phase 3**: Google Gemini
   - 비용 효율적인 대안
   - 긴 컨텍스트 활용 (2M tokens)

---

## 8. 에러 처리 및 Retry 전략

### 8.1 공통 에러 코드

| 에러 | 원인 | 처리 |
|------|------|------|
| 401 | 잘못된 API Key | 키 확인 요청 |
| 429 | Rate Limit | Exponential Backoff |
| 500 | 서버 에러 | Retry (최대 3회) |
| 529 | 과부하 | 대기 후 Retry |

### 8.2 Retry 구현

```java
@Component
public class LlmRetryPolicy {

    private static final int MAX_RETRIES = 3;
    private static final Duration INITIAL_DELAY = Duration.ofSeconds(1);

    public <T> Mono<T> withRetry(Mono<T> operation) {
        return operation
            .retryWhen(Retry.backoff(MAX_RETRIES, INITIAL_DELAY)
                .filter(this::isRetryable)
                .onRetryExhaustedThrow((spec, signal) ->
                    new LlmApiException("Max retries exceeded", signal.failure())
                )
            );
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            return ex.getStatusCode().value() == 429
                || ex.getStatusCode().value() >= 500;
        }
        return false;
    }
}
```

---

## 9. 참고 자료

### Claude API
- [Messages API Reference](https://docs.anthropic.com/en/api/messages)
- [Getting Started](https://docs.anthropic.com/en/docs/get-started)

### OpenAI API
- [Chat Completions](https://platform.openai.com/docs/api-reference/chat)
- [API Reference](https://platform.openai.com/docs/api-reference/introduction)

### Gemini API
- [Gemini API Docs](https://ai.google.dev/gemini-api/docs)
- [Gemini Models](https://ai.google.dev/gemini-api/docs/models)
