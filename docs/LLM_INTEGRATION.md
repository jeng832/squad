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

| 항목 | 내용 |
|------|------|
| Base URL | `https://api.anthropic.com` |
| 주요 엔드포인트 | `POST /v1/messages` |
| 인증 | `x-api-key` 헤더 |
| 특징 | Stateless API (전체 대화 히스토리를 매 요청에 전송) |

### 3.2 주요 모델

| 모델 | 용도 | 컨텍스트 |
|------|------|----------|
| claude-opus-4-5-20251101 | 최고 성능, 복잡한 추론 | 200K tokens |
| claude-sonnet-4-20250514 | 균형잡힌 성능/비용 | 200K tokens |
| claude-haiku-3-5-20241022 | 빠른 응답, 저비용 | 200K tokens |

### 3.3 Request/Response 구조

**Request:**
| 필드 | 타입 | 설명 |
|------|------|------|
| model | string | 사용할 모델 ID |
| max_tokens | integer | 최대 응답 토큰 수 |
| messages | array | 대화 메시지 목록 [{role, content}] |
| system | string | 시스템 프롬프트 (선택) |
| tools | array | Tool Use 정의 (선택) |

**Response:**
| 필드 | 타입 | 설명 |
|------|------|------|
| id | string | 응답 ID |
| content | array | 응답 블록 목록 (텍스트 블록 + tool_use 블록 혼합 가능) |
| stop_reason | string | 종료 사유 (end_turn, tool_use 등) |
| usage | object | 토큰 사용량 {input_tokens, output_tokens} |

### 3.4 Tool Use 지원

Claude의 Tool Use는 **요청 시 tools 목록을 선언**하고, 응답에서 **tool_use 블록으로 실제 호출 의도와 입력값을 반환**하는 방식입니다.

- Tool 정의: `name`, `description`, `input_schema` (JSON Schema)
- Tool을 선언하면 모델이 tool_use를 선택할 수 있음
- 응답의 `content` 배열에는 아래 두 종류 블록이 섞여 나올 수 있음
  - 텍스트 블록: `{"type":"text","text":"..."}`
  - Tool Use 블록: `{"type":"tool_use","id":"...","name":"...","input":{...}}`
- `stop_reason: "tool_use"`이면 **도구 실행이 필요하다는 의미**
  1. tool_use 블록을 파싱해 `name`과 `input`을 얻음
  2. 실제 도구(MCP 등)를 호출
  3. 실행 결과를 다시 Claude에 전달해 후속 응답을 받음

Squad의 `bash_exec`는 보안상 제한된 내장 도구다. 에이전트가 사용할 수 있는 명령은 아래 allowlist로 제한된다.

- `cat`, `cp`, `echo`, `grep`, `head`, `ls`, `mkdir`, `mv`, `pwd`, `tail`, `touch`, `wc`
- `find`가 필요하면 `bash_exec`가 아니라 `file_search` 도구를 사용해야 한다.

**Request 예시:**
```json
{
  "model": "claude-sonnet-4-20250514",
  "max_tokens": 1024,
  "system": "너는 시스템 정보를 요약하는 에이전트다.",
  "messages": [
    {"role": "user", "content": "최신 주문 상태를 조회해줘"}
  ],
  "tools": [
    {
      "name": "get_order_status",
      "description": "주문 상태 조회",
      "input_schema": {
        "type": "object",
        "properties": {
          "orderId": { "type": "string" }
        },
        "required": ["orderId"]
      }
    }
  ]
}
```

**Response 예시 (tool_use):**
```json
{
  "id": "msg_123",
  "content": [
    {
      "type": "tool_use",
      "id": "call_1",
      "name": "get_order_status",
      "input": { "orderId": "ORD-2024-0001" }
    }
  ],
  "stop_reason": "tool_use",
  "usage": { "input_tokens": 120, "output_tokens": 15 }
}
```

**Response 예시 (text):**
```json
{
  "id": "msg_124",
  "content": [
    { "type": "text", "text": "주문 상태는 배송 중입니다." }
  ],
  "stop_reason": "end_turn",
  "usage": { "input_tokens": 120, "output_tokens": 30 }
}
```

### 3.5 참고 문서

- [Messages API Reference](https://docs.anthropic.com/en/api/messages)
- [Getting Started](https://docs.anthropic.com/en/docs/get-started)
- [Tool Use Guide](https://docs.anthropic.com/en/docs/build-with-claude/tool-use)

---

## 4. OpenAI API

### 4.1 API 개요

| 항목 | 내용 |
|------|------|
| Base URL | `https://api.openai.com/v1` |
| 주요 엔드포인트 | `POST /chat/completions` |
| 인증 | `Authorization: Bearer` 헤더 |
| 특징 | 다양한 모델, Function Calling 지원 |

### 4.2 주요 모델

| 모델 | 용도 | 컨텍스트 |
|------|------|----------|
| gpt-5.2 | 최신 플래그십 모델 | 128K tokens |
| gpt-4.1 | 균형잡힌 성능 | 128K tokens |
| gpt-4.1-mini | 빠른 응답, 저비용 | 128K tokens |
| o3-pro | 고급 추론 | 128K tokens |

### 4.3 Request/Response 구조

**Request:**
| 필드 | 타입 | 설명 |
|------|------|------|
| model | string | 사용할 모델 ID |
| messages | array | 대화 메시지 [{role, content}] |
| max_tokens | integer | 최대 응답 토큰 수 (선택) |
| temperature | number | 응답 다양성 0~2 (선택) |
| tools | array | Function Calling 정의 (선택) |

**Response:**
| 필드 | 타입 | 설명 |
|------|------|------|
| id | string | 응답 ID |
| choices | array | 응답 목록 [{message, finish_reason}] |
| usage | object | 토큰 사용량 {prompt_tokens, completion_tokens} |

### 4.4 Function Calling 지원

- Tool 정의: `type: "function"`, `function: {name, description, parameters}`
- `finish_reason: "tool_calls"` 시 함수 호출 처리

### 4.5 참고 문서

- [Chat Completions API](https://platform.openai.com/docs/api-reference/chat)
- [Function Calling Guide](https://platform.openai.com/docs/guides/function-calling)

---

## 5. Google Gemini API

### 5.1 API 개요

| 항목 | 내용 |
|------|------|
| Base URL | `https://generativelanguage.googleapis.com` |
| 주요 엔드포인트 | `POST /v1beta/models/{model}:generateContent` |
| 인증 | API Key 쿼리 파라미터 또는 OAuth |
| 특징 | 멀티모달, MCP 네이티브 지원 |

### 5.2 주요 모델

| 모델 | 용도 | 컨텍스트 |
|------|------|----------|
| gemini-3-pro-preview | 최고 성능, 추론 | 2M tokens |
| gemini-3-flash-preview | 빠른 속도, 비용 효율 | 1M tokens |
| gemini-2.5-flash | 범용 | 1M tokens |

### 5.3 Request/Response 구조

**Request:**
| 필드 | 타입 | 설명 |
|------|------|------|
| contents | array | 대화 내용 [{role, parts}] |
| generationConfig | object | 생성 설정 {temperature, maxOutputTokens} |
| tools | array | Function 정의 (선택) |

**Response:**
| 필드 | 타입 | 설명 |
|------|------|------|
| candidates | array | 응답 후보 [{content, finishReason}] |
| usageMetadata | object | 토큰 사용량 |

### 5.4 Function Calling 지원

- Tool 정의: `functionDeclarations: [{name, description, parameters}]`
- 응답에서 function call 감지 시 처리

### 5.5 참고 문서

- [Gemini API Docs](https://ai.google.dev/gemini-api/docs)
- [Function Calling](https://ai.google.dev/gemini-api/docs/function-calling)

---

## 6. 공통 인터페이스 설계

### 6.1 LLM Provider 추상화

Squad에서는 여러 LLM Provider를 통일된 인터페이스로 추상화합니다.

```
┌─────────────────────────────────────────────────────────────┐
│                      LlmProvider                             │
│  (Interface)                                                 │
├─────────────────────────────────────────────────────────────┤
│  + sendMessage(request) → response                          │
│  + streamMessage(request) → stream                          │
│  + getProviderName() → string                               │
│  + getSupportedModels() → list                              │
└─────────────────────────────────────────────────────────────┘
                           △
           ┌───────────────┼───────────────┐
           │               │               │
┌──────────┴──┐   ┌───────┴───┐   ┌───────┴───┐
│ClaudeProvider│   │OpenAiProvider│   │GeminiProvider│
└─────────────┘   └─────────────┘   └─────────────┘
```

### 6.2 공통 데이터 모델

**LlmRequest:**
| 필드 | 설명 |
|------|------|
| model | 모델 ID |
| systemPrompt | 시스템 프롬프트 |
| messages | 대화 메시지 목록 |
| maxTokens | 최대 토큰 |
| temperature | 응답 다양성 |
| tools | Tool 정의 목록 |

**LlmResponse:**
| 필드 | 설명 |
|------|------|
| id | 응답 ID |
| content | 응답 텍스트 |
| finishReason | 종료 사유 |
| toolCalls | Tool 호출 목록 |
| usage | 토큰 사용량 |

### 6.3 Provider Factory 패턴

- 에이전트 설정의 `provider` 필드로 적절한 Provider 선택
- 런타임에 Provider 교체 가능
- 새로운 Provider 추가 시 구현체만 추가

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
   - Squad의 초기 목표가 코딩 도메인
   - Claude Code 등으로 검증된 코딩 능력

2. **간결한 API 구조**
   - Messages API가 단순하고 직관적
   - Stateless 방식으로 구현이 간단

3. **Tool Use 지원**
   - MCP 연동에 필요한 Tool Use 잘 지원
   - Function Calling 안정성 높음

4. **긴 컨텍스트**
   - 200K 토큰으로 여러 레포 분석에 적합

### 7.3 역할별 모델 배정

| 역할 | 모델 | 이유 |
|------|------|------|
| Orchestrator | claude-sonnet-4 | 빠른 판단, 균형잡힌 성능/비용 |
| Worker | claude-sonnet-4 | 코딩 작업에 적합 |
| Analyst | claude-sonnet-4 | 분석 작업에 충분 |
| Scribe | claude-haiku-3.5 | 문서화는 저비용 모델로 충분 |

### 7.4 확장 계획

| Phase | Provider | 이유 |
|-------|----------|------|
| Phase 1 (MVP) | Claude | 코딩 특화, API 안정성 |
| Phase 2 | OpenAI | 사용자 선택권 확대 |
| Phase 3 | Gemini | 비용 효율, 긴 컨텍스트 (2M) |

---

## 8. 에러 처리 전략

### 8.1 공통 에러 코드

| 에러 | 원인 | 처리 방법 |
|------|------|----------|
| 401 | 잘못된 API Key | 키 확인 요청, 실패 반환 |
| 429 | Rate Limit 초과 | Exponential Backoff 후 재시도 |
| 500 | 서버 에러 | 최대 3회 재시도 |
| 529 | 과부하 | 대기 후 재시도 |

### 8.2 Retry 정책

| 항목 | 값 |
|------|-----|
| 최대 재시도 횟수 | 3회 |
| 초기 대기 시간 | 1초 |
| Backoff 방식 | Exponential (1s → 2s → 4s) |
| 재시도 대상 | 429, 5xx 에러 |

---

## 9. 참고 자료

### Claude API
- [Messages API Reference](https://docs.anthropic.com/en/api/messages)
- [Getting Started](https://docs.anthropic.com/en/docs/get-started)
- [Tool Use Guide](https://docs.anthropic.com/en/docs/build-with-claude/tool-use)

### OpenAI API
- [Chat Completions](https://platform.openai.com/docs/api-reference/chat)
- [Function Calling](https://platform.openai.com/docs/guides/function-calling)

### Gemini API
- [Gemini API Docs](https://ai.google.dev/gemini-api/docs)
- [Function Calling](https://ai.google.dev/gemini-api/docs/function-calling)
