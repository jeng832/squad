# Squad 시스템 아키텍처 설계서

## 1. 개요

### 1.1 문서 목적
Squad 플랫폼의 전체 시스템 아키텍처와 주요 모듈 구조를 정의합니다.

### 1.2 시스템 개요
Squad는 멀티 AI 에이전트 협업 플랫폼으로, 여러 AI 에이전트가 Orchestrator의 조율 하에 복잡한 작업을 수행합니다. 각 에이전트는 독립적인 Docker 컨테이너(샌드박스)로 실행되어 **완전한 격리**를 보장합니다.

**핵심 설계 원칙:**
- 모든 Agent는 **독립적인 샌드박스 환경**에서 동작
- 동일 Squad Template으로 **여러 Session 동시 실행 가능** (예: 기능 A 개발 + 기능 B 개발)
- 각 Active Squad의 Agent는 자신만의 **Workspace**를 가지며, Git Repository 등을 독립적으로 clone
- Session 간 완전한 격리로 **동시 작업 충돌 방지**

### 1.3 기술 스택

| 항목 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Build | Gradle |
| Database | MySQL 8.x |
| Message Queue | Redis (Pub/Sub) |
| Container | Docker |
| Container Orchestration | Docker Compose |
| API | REST API |
| Real-time | WebSocket (STOMP) |
| Agent Communication | HTTP (Docker Network) |
| CLI | Picocli + JLine3 |

---

## 2. 시스템 전체 아키텍처

### 2.1 High-Level 아키텍처

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                 Client                                       │
│         Web UI (React) / CLI Shell (Picocli+JLine3) / REST API Clients      │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Squad Platform Server                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │ Agent Mgmt  │  │ Squad Mgmt  │  │ Session Mgmt│  │  Monitoring │        │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────────┐         │
│  │  MCP Mgmt   │  │ Skill Mgmt  │  │     Agent Container Mgmt    │         │
│  └─────────────┘  └─────────────┘  └─────────────────────────────┘         │
│  ┌─────────────────────────────────────────────────────────────┐           │
│  │                       MCP Gateway                            │           │
│  │  MCP 서버 중앙 관리, 에이전트에게 SSE/HTTP로 도구 제공        │           │
│  └─────────────────────────────────────────────────────────────┘           │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │
              ┌───────────────────┼───────────────────┐
              │                   │                   │
              ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Agent Containers (Docker)                             │
│                                                                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐              │
│  │  Orchestrator   │  │    Worker A     │  │    Worker B     │              │
│  │   Container     │  │   Container     │  │   Container     │              │
│  │                 │  │                 │  │                 │              │
│  │ ┌─────────────┐ │  │ ┌─────────────┐ │  │ ┌─────────────┐ │              │
│  │ │ Agent Runner│ │  │ │ Agent Runner│ │  │ │ Agent Runner│ │              │
│  │ │ LLM Client  │ │  │ │ LLM Client  │ │  │ │ LLM Client  │ │              │
│  │ │ Built-in    │ │  │ │ Built-in    │ │  │ │ Built-in    │ │              │
│  │ │ Tools       │ │  │ │ Tools       │ │  │ │ Tools       │ │              │
│  │ └─────────────┘ │  │ └─────────────┘ │  │ └─────────────┘ │              │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘              │
│           │                    │                    │                        │
│           └────────────────────┼────────────────────┘                        │
│                                │ SSE/HTTP (MCP Gateway 연결)                 │
│                    Docker Network (squad-network)                            │
└─────────────────────────────────────────────────────────────────────────────┘
                                  │
              ┌───────────────────┼───────────────────┐
              ▼                   ▼                   ▼
        ┌──────────┐        ┌──────────┐        ┌──────────┐
        │  MySQL   │        │  Redis   │        │ LLM APIs │
        │ (Data)   │        │ (Pub/Sub)│        │ (Claude) │
        └──────────┘        └──────────┘        └──────────┘
```

### 2.2 주요 컴포넌트

| 컴포넌트 | 역할 |
|----------|------|
| **Squad CLI** | 인터랙티브 터미널 클라이언트. 슬래시 커맨드, 가이드 폼, 실시간 모니터링 제공. REST API + WebSocket으로 Platform Server와 통신 |
| **Squad Platform Server** | 에이전트/Squad/세션 관리, API 제공, 모니터링 |
| **MCP Gateway** | MCP 서버 프로세스를 중앙에서 관리하고, 에이전트에게 SSE/HTTP 엔드포인트로 외부 도구 제공 |
| **Agent Container** | 개별 에이전트 실행 환경, LLM 연동, Built-in Tools 실행 |
| **MySQL** | 에이전트, Squad, 세션, 메시지 등 영속 데이터 저장 |
| **Redis** | 에이전트 간 메시지 전달 (Pub/Sub), 실시간 상태 공유. 메시징은 인터페이스 기반으로 추상화되어 있으며, `squad.messaging.provider` 설정으로 구현체 전환 가능 |
| **LLM APIs** | Claude, OpenAI 등 외부 LLM 서비스 |

---

## 3. Agent Container 아키텍처

### 3.1 개별 Agent 컨테이너 구조

각 에이전트는 독립적인 Docker 컨테이너로 실행됩니다.

```
┌──────────────────────────────────────────────────────┐
│                   Agent Container                     │
│                                                       │
│  ┌─────────────────────────────────────────────────┐ │
│  │                  Agent Runner                    │ │
│  │  - 에이전트 설정 로드                            │ │
│  │  - 메시지 수신/발신                              │ │
│  │  - 작업 실행 관리                                │ │
│  └─────────────────────────────────────────────────┘ │
│                         │                             │
│         ┌──────────┼──────────┼──────────┐           │
│         ▼          ▼          ▼          ▼           │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐ │
│  │LLM Client│ │Built-in  │ │MCP GW    │ │Message │ │
│  │          │ │Tools     │ │Client    │ │Handler │ │
│  │- Claude  │ │- file_   │ │          │ │        │ │
│  │- OpenAI  │ │  read/   │ │- SSE/    │ │- Redis │ │
│  │- Gemini  │ │  write   │ │  HTTP    │ │  Pub/  │ │
│  │          │ │- file_   │ │  연결    │ │  Sub   │ │
│  │          │ │  search  │ │          │ │        │ │
│  │          │ │- bash_   │ │          │ │        │ │
│  │          │ │  exec    │ │          │ │        │ │
│  └──────────┘ └──────────┘ └──────────┘ └────────┘ │
│                                                       │
│  Environment Variables:                               │
│  - AGENT_ID                                          │
│  - AGENT_CONFIG (JSON)                               │
│  - REDIS_URL                                         │
│  - PLATFORM_API_URL                                  │
└──────────────────────────────────────────────────────┘
```

### 3.2 Agent 컨테이너 생명주기

```
1. 세션 시작 요청
        │
        ▼
2. Platform Server가 필요한 Agent Container 생성
        │
        ├─→ Orchestrator Container 시작
        ├─→ Worker A Container 시작
        └─→ Worker B Container 시작
        │
        ▼
3. Orchestrator가 작업 분배 (Redis Pub/Sub)
        │
        ▼
4. Worker들이 작업 수행 후 결과 반환
        │
        ▼
5. 세션 완료 시 Container 정리
```

### 3.3 Agent 간 통신

```
┌─────────────────┐                              ┌─────────────────┐
│  Orchestrator   │                              │    Worker A     │
│   Container     │                              │   Container     │
│                 │                              │                 │
│  1. Publish     │    ┌──────────────────┐     │  2. Subscribe   │
│     Task ──────────▶ │ Redis Channel    │ ────────▶ Receive     │
│                 │    │ session:{id}     │     │     Task        │
│  4. Receive  ◀─────  │                  │  ◀────── 3. Publish   │
│     Result      │    └──────────────────┘     │     Result      │
└─────────────────┘                              └─────────────────┘
```

**통신 채널 구조 (Redis 구현):**
- `session:{sessionId}:orchestrator` - Orchestrator 전용 채널
- `session:{sessionId}:agent:{agentId}` - 개별 Agent 채널
- `session:{sessionId}:broadcast` - 전체 브로드캐스트

**메시징 추상화 구조:**
```
messaging/
├── MessagePublisher          (인터페이스 - 도메인 포트)
├── SessionMessage            (메시지 DTO)
└── redis/                    (Redis 구현 어댑터)
    ├── RedisMessagePublisher (@ConditionalOnProperty)
    ├── RedisMessageConfig    (@ConditionalOnProperty)
    └── RedisChannelConstants (package-private, 채널 네이밍)
```
- `MessagePublisher` 인터페이스를 통해 발행 동작을 추상화
- `squad.messaging.provider` 설정으로 구현체 전환 (기본값: `redis`)
- 향후 Kafka, AWS SNS 등 추가 시 `messaging/kafka/` 패키지만 추가하면 됨

**메시징 의존 빈의 조건부 등록:**

메시징 인프라에 의존하는 빈들은 `@ConditionalOnBean` / `@ConditionalOnProperty`를 사용하여 메시징이 비활성화된 환경에서도 애플리케이션이 정상 기동되도록 설계되어 있다.

| 빈 | 조건부 어노테이션 | 설명 |
|----|-------------------|------|
| `RedisMessagePublisher` | `@ConditionalOnProperty(name="squad.messaging.provider", havingValue="redis")` | Redis 메시징 활성화 시에만 등록 |
| `RedisMessageSubscriber` | `@ConditionalOnProperty(name="squad.messaging.provider", havingValue="redis")` | Redis 메시징 활성화 시에만 등록 |
| `DefaultMessageRouter` | `@ConditionalOnBean(MessagePublisher.class)` | MessagePublisher 존재 시에만 등록 (provider 무관) |

메시징이 비활성화되면(`squad.messaging.provider=none`) 위 빈들이 모두 생성되지 않는다. 단, `SessionExecutionService`처럼 메시징에 필수 의존하는 서비스는 조건부 빈이 아닌 **필수 의존**으로 유지한다. 운영 환경에서 메시징은 반드시 활성화되어야 하며, 누락 시 애플리케이션이 즉시 실패(fail-fast)하는 것이 올바른 동작이다.

### 3.4 샌드박스 및 Workspace 구조

각 Agent Container는 완전히 격리된 샌드박스 환경에서 동작하며, 독립적인 Workspace를 가집니다.

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           Agent Container (Sandbox)                           │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                           /workspace                                     │ │
│  │  (Agent의 독립적인 작업 디렉토리)                                         │ │
│  │                                                                          │ │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐         │ │
│  │  │ order-service/  │  │payment-service/ │  │ common-lib/     │         │ │
│  │  │ (git clone)     │  │ (git clone)     │  │ (git clone)     │         │ │
│  │  │                 │  │                 │  │                 │         │ │
│  │  │ branch: feat/X  │  │ branch: feat/X  │  │ branch: main    │         │ │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘         │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                           /tmp                                           │ │
│  │  (임시 파일, 분석 결과 등)                                                │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                               │
│  Environment:                                                                 │
│  - 독립적인 파일시스템                                                        │
│  - 격리된 프로세스 공간                                                       │
│  - 제한된 네트워크 (squad-network만 허용)                                     │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Workspace 특징:**
- 각 Agent는 자신만의 `/workspace` 디렉토리 보유
- Git Repository는 Agent별로 **독립적으로 clone**
- 브랜치 선택은 Agent가 자율적으로 판단
- Session 간 Workspace는 완전히 분리 (동일 Squad Template으로 여러 Session 실행 시에도 격리)

### 3.5 동시 세션 실행 아키텍처

동일한 Squad Template으로 여러 Session을 동시에 실행할 수 있습니다. 각 Session은 독립적인 Active Squad를 생성합니다.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              Squad Platform Server                               │
│                                                                                  │
│   Squad Template: "기능 개발 Squad"                                              │
│   - Orchestrator: "orch-001"                                                    │
│   - Workers: ["order-worker", "payment-worker"]                                 │
└─────────────────────────────────┬───────────────────────────────────────────────┘
                                  │
              ┌───────────────────┴───────────────────┐
              │                                       │
              ▼                                       ▼
┌─────────────────────────────────────┐ ┌─────────────────────────────────────┐
│         Session A (기능 X 개발)      │ │         Session B (기능 Y 개발)      │
│         ID: sess-001                │ │         ID: sess-002                │
│                                     │ │                                     │
│  ┌────────────────────────────────┐ │ │  ┌────────────────────────────────┐ │
│  │ Container: squad-sess001-orch  │ │ │  │ Container: squad-sess002-orch  │ │
│  │ Workspace:                     │ │ │  │ Workspace:                     │ │
│  │   /workspace/order → feat/X   │ │ │  │   /workspace/order → feat/Y   │ │
│  └────────────────────────────────┘ │ │  └────────────────────────────────┘ │
│                                     │ │                                     │
│  ┌────────────────────────────────┐ │ │  ┌────────────────────────────────┐ │
│  │ Container: squad-sess001-order │ │ │  │ Container: squad-sess002-order │ │
│  │ Workspace:                     │ │ │  │ Workspace:                     │ │
│  │   /workspace/order → feat/X   │ │ │  │   /workspace/order → feat/Y   │ │
│  └────────────────────────────────┘ │ │  └────────────────────────────────┘ │
│                                     │ │                                     │
│  ┌────────────────────────────────┐ │ │  ┌────────────────────────────────┐ │
│  │ Container: squad-sess001-pay   │ │ │  │ Container: squad-sess002-pay   │ │
│  │ Workspace:                     │ │ │  │ Workspace:                     │ │
│  │   /workspace/payment → feat/X │ │ │  │   /workspace/payment → feat/Y │ │
│  └────────────────────────────────┘ │ │  └────────────────────────────────┘ │
│                                     │ │                                     │
│  Redis Channel:                     │ │  Redis Channel:                     │
│    session:sess-001:*               │ │    session:sess-002:*               │
└─────────────────────────────────────┘ └─────────────────────────────────────┘

     Session A와 Session B는 완전히 격리됨
     - 독립적인 Container
     - 독립적인 Workspace (각자 Git clone)
     - 독립적인 Redis Channel
     - 서로 다른 브랜치에서 동시 작업 가능
```

**동시 세션 실행의 이점:**
- 동일 Squad Template으로 여러 기능을 **병렬 개발** 가능
- 각 Active Squad는 **충돌 없이** 독립적으로 작업
- 각 Active Squad의 Agent가 **서로 다른 브랜치**에서 작업 가능
- 하나의 Session 실패가 다른 Session에 **영향 없음**

---

## 4. 모듈 구조

### 4.1 Platform Server 모듈

```
┌─────────────────────────────────────────────────────────────────┐
│                      Squad Platform Server                       │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                      API Layer                              │ │
│  │  Agent API │ Squad API │ Session API │ MCP API │ Skill API │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              │                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                    Service Layer                            │ │
│  │  AgentService │ SquadService │ SessionService │ MonitorSvc │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              │                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                 Container Management                        │ │
│  │  AgentContainerManager │ DockerClient │ HealthChecker      │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              │                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                   Infrastructure                            │ │
│  │  Repository │ SecretStore │ EventPublisher │ RedisClient   │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 CLI 모듈 (`:squad-cli`)

```
┌─────────────────────────────────────────────────────────────────┐
│                         Squad CLI                                │
│                   (Picocli + JLine3)                             │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                   Interactive Shell                         │ │
│  │  CommandPalette │ FuzzySearch │ KeyBindingManager          │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              │                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                    Slash Commands                           │ │
│  │  /agent │ /squad │ /session │ /mcp │ /skill │ /secret     │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              │                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                    UI Components                            │ │
│  │  TableRenderer │ GuidedForm │ MultiSelect │ StatusDisplay  │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              │                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                    API Client Layer                         │ │
│  │  RestClient (REST API) │ StompClient (WebSocket 모니터링)  │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

**설계 원칙:**
- CLI는 **순수 REST 클라이언트**로 동작 (백엔드 도메인/DB 모듈에 직접 의존하지 않음)
- 실시간 모니터링은 **WebSocket (STOMP)** 으로 세션 이벤트 구독
- **인터랙티브 모드** (기본): `squad` 실행 시 대화형 셸 진입
- **One-shot 모드**: `squad agent list` 처럼 인자와 함께 실행 시 결과 출력 후 종료
- Java 21 **Virtual Thread** 활용으로 REST 호출/WebSocket 이벤트 처리 병렬화

### 4.3 Agent Container 모듈

```
┌─────────────────────────────────────────────────────────────────┐
│                       Agent Container                            │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                     Agent Runner                            │ │
│  │  ConfigLoader │ TaskExecutor │ LifecycleManager            │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              │                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                    LLM Integration                          │ │
│  │  LlmProvider │ PromptBuilder │ ResponseParser              │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              │                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                    Built-in Tools                           │ │
│  │  FileReadTool │ FileWriteTool │ FileSearchTool │ BashTool  │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              │                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                  MCP Gateway Client                         │ │
│  │  McpGatewayConnector │ ToolExecutor │ SSE/HTTP Client      │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              │                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                   Message Handler                           │ │
│  │  RedisSubscriber │ MessageRouter │ ResponseSender          │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. 핵심 모듈 상세 설계

### 5.1 세션 실행 흐름

```
┌────────┐      ┌──────────────┐      ┌─────────────────┐      ┌─────────────┐
│ Client │      │Platform Server│      │  Orchestrator   │      │   Worker    │
└───┬────┘      └──────┬───────┘      │   Container     │      │  Container  │
    │                  │              └────────┬────────┘      └──────┬──────┘
    │  POST /sessions  │                       │                      │
    │  {squad, prompt} │                       │                      │
    │─────────────────>│                       │                      │
    │                  │                       │                      │
    │                  │ 1. Create Session     │                      │
    │                  │ 2. Load Squad Config  │                      │
    │                  │ 3. Start Containers   │                      │
    │                  │──────────────────────>│                      │
    │                  │                       │──────────────────────>
    │                  │                       │                      │
    │                  │ 4. Send Initial       │                      │
    │                  │    Prompt via Redis   │                      │
    │                  │──────────────────────>│                      │
    │                  │                       │                      │
    │                  │                       │ 5. Analyze & Delegate│
    │                  │                       │──────────────────────>
    │                  │                       │                      │
    │                  │                       │    6. Task Result    │
    │                  │                       │<─────────────────────│
    │                  │                       │                      │
    │                  │                       │ 7. Compile Result    │
    │                  │  8. Final Result      │                      │
    │                  │<──────────────────────│                      │
    │                  │                       │                      │
    │  Session Result  │ 9. Cleanup Containers │                      │
    │<─────────────────│──────────────────────>│──────────────────────>
    │                  │                       │                      │
```

### 5.2 Agent Runner 동작 흐름

```
START Agent Container
    │
    ▼
┌─────────────────────────────────┐
│  1. Load Configuration          │
│     - Read AGENT_CONFIG env     │
│     - Parse agent settings      │
│     - Initialize LLM client     │
└─────────────────┬───────────────┘
                  │
                  ▼
┌─────────────────────────────────┐
│  2. Connect to Redis            │
│     - Subscribe to agent channel│
│     - Register heartbeat        │
└─────────────────┬───────────────┘
                  │
                  ▼
┌─────────────────────────────────┐
│  3. Wait for Message            │◄─────────────────┐
└─────────────────┬───────────────┘                  │
                  │                                   │
                  ▼                                   │
┌─────────────────────────────────┐                  │
│  4. Process Message             │                  │
│     ├─ Build prompt (role +     │                  │
│     │   skills + context)       │                  │
│     ├─ Call LLM API             │                  │
│     └─ Parse response           │                  │
└─────────────────┬───────────────┘                  │
                  │                                   │
                  ▼                                   │
        ┌─────────────────┐                          │
        │  Tool Call?     │                          │
        └────────┬────────┘                          │
           Yes   │   No                              │
         ┌───────┴───────┐                           │
         ▼               ▼                           │
┌─────────────┐  ┌─────────────┐                    │
│ Execute MCP │  │Send Response│                    │
│ Tool        │  │ via Redis   │                    │
└──────┬──────┘  └─────────────┘                    │
       │                                             │
       ▼                                             │
┌─────────────┐                                      │
│ Continue    │──────────────────────────────────────┘
│ Conversation│
└─────────────┘
```

### 5.3 Orchestrator 작업 분배 로직

```
RECEIVE user_prompt

FUNCTION orchestrate(prompt, squad_agents):

    # 1. 작업 분석
    analysis = CALL LLM with:
        system: "You are an orchestrator. Analyze the task and
                 decide which agents to delegate to."
        user: prompt
        tools: [delegate_task, request_help, complete_session]

    # 2. 작업 분배 루프
    WHILE session not complete:

        IF analysis contains delegate_task:
            FOR EACH delegation in analysis.tool_calls:
                agent_id = delegation.agent_id
                task = delegation.task

                # 에이전트에게 작업 전달
                PUBLISH to "session:{id}:agent:{agent_id}":
                    { type: "TASK_REQUEST", task: task }

                # 결과 대기
                result = AWAIT from "session:{id}:orchestrator"

                # 결과 축적
                context.add(agent_id, result)

        IF analysis contains request_help:
            # 다른 에이전트에게 도움 요청
            helper_id = analysis.helper_agent
            question = analysis.question

            PUBLISH help request
            help_result = AWAIT response

            # 원래 에이전트에게 전달
            PUBLISH help_result to original agent

        IF analysis contains complete_session:
            final_result = analysis.result
            RETURN final_result

        # 다음 액션 결정
        analysis = CALL LLM with:
            context: accumulated_results
            tools: [delegate_task, request_help, complete_session]

    RETURN final_result
```

### 5.4 LLM Provider 처리 흐름

```
┌─────────────────────────────────────────────────────────────────┐
│                        LLM Provider                              │
│                                                                  │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │   Request   │    │   Provider  │    │  Response   │         │
│  │  Adapter    │───>│   Client    │───>│  Adapter    │         │
│  └─────────────┘    └─────────────┘    └─────────────┘         │
│         │                  │                  │                  │
│         ▼                  ▼                  ▼                  │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │ LlmRequest  │    │ HTTP Call   │    │ LlmResponse │         │
│  │ (Common)    │    │ + Retry     │    │ (Common)    │         │
│  └─────────────┘    └─────────────┘    └─────────────┘         │
└─────────────────────────────────────────────────────────────────┘

Provider Selection:

    INPUT: agent.llm_config.provider (e.g., "claude")

    MATCH provider:
        "claude"  → ClaudeProvider
        "openai"  → OpenAiProvider
        "gemini"  → GeminiProvider
        default   → throw UnsupportedProviderException
```

### 5.5 도구 실행 흐름

에이전트의 도구 호출은 **Built-in Tool**과 **MCP Tool** 두 경로로 분기된다.

```
RECEIVE tool_call from LLM response

FUNCTION execute_tool(tool_call):

    # 1. Built-in Tool 여부 확인
    IF tool_call.name in BUILT_IN_TOOLS:
        # Agent Runtime 내부에서 직접 실행
        MATCH tool_call.name:
            "file_read"   → read file from /workspace
            "file_write"  → write file to /workspace
            "file_search" → search files in /workspace
            "bash_exec"   → execute shell command

        # 보안 검증: /workspace 밖 접근 차단
        validate_path(tool_call.arguments.path, "/workspace")

        RETURN execute_locally(tool_call)

    # 2. MCP Tool → MCP Gateway 경유
    ELSE:
        mcp_id = find_mcp_for_tool(tool_call.name)

        # MCP Gateway에 SSE/HTTP 요청
        request = {
            jsonrpc: "2.0",
            method: "tools/call",
            params: {
                name: tool_call.name,
                arguments: tool_call.arguments
            }
        }

        response = HTTP_POST to MCP_GATEWAY_URL/mcp/{mcp_id}/call
            with body: request

        IF response.error:
            RETURN { error: response.error.message }
        ELSE:
            RETURN { result: response.result }
```

**MCP Gateway 내부 흐름 (Platform Server 측):**

```
RECEIVE tool_call request from Agent (SSE/HTTP)

FUNCTION gateway_execute(mcp_id, tool_call):

    # 1. MCP 프로세스 확인 (McpProcessManager 활용)
    IF mcp_id NOT in active_connections:
        connection = McpProcessManager.start(mcp_config)

    # 2. stdin/stdout으로 JSON-RPC 통신
    connection = McpProcessManager.getConnection(mcp_id)
    SEND request to connection.stdin
    response = READ from connection.stdout

    RETURN response to Agent
```

### 5.6 Container 생명주기 관리

```
┌──────────────────────────────────────────────────────────────────┐
│                   Container Lifecycle Manager                     │
└──────────────────────────────────────────────────────────────────┘

SESSION START:
    ┌─────────────────────────────────────────────────────────────┐
    │  1. Load squad configuration                                 │
    │  2. FOR EACH agent in squad:                                │
    │       - Pull image if not exists                            │
    │       - Create container with:                              │
    │           name: "squad-{sessionId}-{agentId}"              │
    │           image: "squad-agent:latest"                       │
    │           env: AGENT_ID, AGENT_CONFIG, REDIS_URL           │
    │           network: squad-network                            │
    │           volumes: /var/squad/sessions/{sessionId}/{agentId}│
    │                    → /workspace (Container 내부)            │
    │       - Start container                                     │
    │       - Wait for health check                               │
    │  3. Register containers in session state                    │
    │  4. Initialize workspace (Agent가 필요한 repo clone)         │
    └─────────────────────────────────────────────────────────────┘

SESSION RUNNING:
    ┌─────────────────────────────────────────────────────────────┐
    │  - Monitor container health (every 10s)                     │
    │  - Restart failed containers (max 3 times)                  │
    │  - Log container stdout/stderr                              │
    │  - Workspace 상태 보존 (재시작 시에도 유지)                    │
    └─────────────────────────────────────────────────────────────┘

SESSION END:
    ┌─────────────────────────────────────────────────────────────┐
    │  1. Send shutdown signal to all containers                  │
    │  2. Wait for graceful shutdown (timeout: 30s)               │
    │  3. Force kill remaining containers                         │
    │  4. Remove containers                                        │
    │  5. Cleanup workspace directories:                          │
    │     - 성공: 즉시 삭제                                        │
    │     - 실패: 설정된 기간 보존 후 삭제 (디버깅용)               │
    │  6. Cleanup session state                                   │
    └─────────────────────────────────────────────────────────────┘

HEALTH CHECK:
    ┌─────────────────────────────────────────────────────────────┐
    │  GET /health on each container                              │
    │                                                              │
    │  Response:                                                   │
    │    { status: "healthy", lastActivity: timestamp }           │
    │                                                              │
    │  IF no response in 30s → mark unhealthy                    │
    │  IF unhealthy 3 times → restart container                  │
    └─────────────────────────────────────────────────────────────┘
```

### 5.7 Workspace 생명주기 관리

```
WORKSPACE CREATION (Session 시작 시):
    ┌─────────────────────────────────────────────────────────────┐
    │  Host 경로: /var/squad/sessions/{sessionId}/{agentId}/      │
    │  Container 경로: /workspace/                                 │
    │                                                              │
    │  1. Host에 session/agent별 디렉토리 생성                      │
    │  2. Volume mount로 Container에 연결                          │
    │  3. Agent 시작 시 필요한 Git repository clone                │
    │     (Agent가 자율적으로 판단)                                 │
    └─────────────────────────────────────────────────────────────┘

WORKSPACE ISOLATION:
    ┌─────────────────────────────────────────────────────────────┐
    │  Session A (sess-001)           Session B (sess-002)        │
    │                                                              │
    │  /var/squad/sessions/           /var/squad/sessions/        │
    │    └── sess-001/                  └── sess-002/             │
    │        ├── orch-001/                  ├── orch-001/         │
    │        │   └── workspace/             │   └── workspace/    │
    │        ├── worker-001/                ├── worker-001/       │
    │        │   └── workspace/             │   └── workspace/    │
    │        └── worker-002/                └── worker-002/       │
    │            └── workspace/                 └── workspace/    │
    │                                                              │
    │  → 완전히 분리된 디렉토리 구조                                │
    │  → 동일 repo를 다른 브랜치로 동시 작업 가능                   │
    └─────────────────────────────────────────────────────────────┘

WORKSPACE CLEANUP:
    ┌─────────────────────────────────────────────────────────────┐
    │  Session 완료 상태에 따른 정리 정책:                          │
    │                                                              │
    │  SUCCESS:                                                    │
    │    - 즉시 workspace 디렉토리 삭제                            │
    │    - rm -rf /var/squad/sessions/{sessionId}/                │
    │                                                              │
    │  FAILED / ERROR:                                             │
    │    - workspace.retention.failed 설정값만큼 보존              │
    │    - 기본값: 24시간                                          │
    │    - 만료 후 background job이 삭제                           │
    │                                                              │
    │  CANCELLED:                                                  │
    │    - workspace.retention.cancelled 설정값만큼 보존           │
    │    - 기본값: 1시간                                           │
    └─────────────────────────────────────────────────────────────┘
```

---

## 6. 데이터 모델

### 5.1 ERD

> **참고**: ERD의 FK 표시는 논리적 관계를 나타냅니다. 실제 DDL에는 명시적 FK 제약조건을 정의하지 않으며, 참조 무결성은 애플리케이션 트랜잭션 내에서 로직으로 처리합니다.

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│    agents    │       │    squads    │       │   sessions   │
├──────────────┤       ├──────────────┤       ├──────────────┤
│ id (PK)      │       │ id (PK)      │       │ id (PK)      │
│ name         │       │ name         │       │ squad_id (FK)│
│ role_type    │◄──────│ orchestrator │       │ user_prompt  │
│ role         │       │ description  │◄──────│ status       │
│ llm_config   │       │ direct_comm  │       │ result       │
│ created_at   │       │ created_at   │       │ started_at   │
└──────────────┘       └──────────────┘       └──────┬───────┘
       │                      │                      │
       │                      ▼                      ▼
       │              ┌──────────────┐       ┌──────────────┐
       │              │ squad_agents │       │   messages   │
       │              ├──────────────┤       ├──────────────┤
       └──────────────│ squad_id(FK) │       │ id (PK)      │
                      │ agent_id(FK) │       │ session_id   │
                      └──────────────┘       │ from_agent   │
                                             │ to_agent     │
┌──────────────┐       ┌──────────────┐      │ content      │
│     mcps     │       │    skills    │      │ type         │
├──────────────┤       ├──────────────┤      └──────────────┘
│ id (PK)      │       │ id (PK)      │
│ name         │       │ name         │      ┌──────────────┐
│ description  │       │ description  │      │   secrets    │
│ config (JSON)│       │ prompt       │      ├──────────────┤
└──────────────┘       │ required_mcp │      │ id (PK)      │
                       └──────────────┘      │ name         │
                                             │ value (AES)  │
                                             └──────────────┘
```

### 5.2 주요 엔티티

| 엔티티 | 설명 |
|--------|------|
| **Agent** | AI 에이전트 정의 (이름, 역할, LLM 설정, MCP/Skill 매핑) |
| **Squad** | 에이전트 팀 구성 (Orchestrator 지정, 멤버 에이전트 목록) |
| **Session** | 작업 실행 단위 (Squad, 프롬프트, 상태, 결과) |
| **Message** | 에이전트 간 메시지 (발신자, 수신자, 내용, 타입) |
| **MCP** | Model Context Protocol 설정 |
| **Skill** | 재사용 가능한 프롬프트 템플릿 |
| **Secret** | 암호화된 API 키 등 민감 정보 |

### 5.3 데이터 무결성 정책

| 항목 | 방식 |
|------|------|
| **FK 제약조건** | 사용하지 않음 (DDL에 FK 정의 없음) |
| **참조 무결성** | 애플리케이션 레벨에서 트랜잭션 내 로직으로 처리 |
| **삭제 처리** | Soft Delete 또는 Service 계층에서 연관 데이터 검증 후 삭제 |

**설계 결정 사유:**
- 성능: FK 검증 오버헤드 제거
- 유연성: 데이터 마이그레이션 및 스키마 변경 용이
- 제어: 비즈니스 로직에 맞는 세밀한 무결성 검증 가능

---

## 6. API 설계

### 6.1 REST API

| Resource | Method | Endpoint | Description |
|----------|--------|----------|-------------|
| Agent | GET | /api/v1/agents | 에이전트 목록 |
| Agent | POST | /api/v1/agents | 에이전트 생성 |
| Agent | GET | /api/v1/agents/{id} | 에이전트 상세 |
| Agent | PUT | /api/v1/agents/{id} | 에이전트 수정 |
| Agent | DELETE | /api/v1/agents/{id} | 에이전트 삭제 |
| Squad | GET | /api/v1/squads | Squad 목록 |
| Squad | POST | /api/v1/squads | Squad 생성 |
| Squad | GET | /api/v1/squads/{id} | Squad 상세 |
| Squad | PUT | /api/v1/squads/{id} | Squad 수정 |
| Squad | DELETE | /api/v1/squads/{id} | Squad 삭제 |
| Session | GET | /api/v1/sessions | 세션 목록 |
| Session | POST | /api/v1/sessions | 세션 시작 |
| Session | GET | /api/v1/sessions/{id} | 세션 상세 |
| Session | POST | /api/v1/sessions/{id}/cancel | 세션 취소 |
| Session | GET | /api/v1/sessions/{id}/messages | 메시지 조회 |

### 6.2 WebSocket API

**연결**: `ws://localhost:8080/ws/sessions/{sessionId}`

**이벤트 타입:**
| Type | Direction | Description |
|------|-----------|-------------|
| AGENT_STATUS | Server→Client | 에이전트 상태 변경 |
| MESSAGE | Server→Client | 에이전트 간 메시지 |
| SESSION_COMPLETE | Server→Client | 세션 완료 |

---

## 7. 실행 환경

### 7.1 Docker Compose 구성

```
┌─────────────────────────────────────────────────────────────────┐
│                         Docker Host                              │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    squad-network                             ││
│  │                                                              ││
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       ││
│  │  │ squad-server │  │    mysql     │  │    redis     │       ││
│  │  │   :8080      │  │   :3306      │  │   :6379      │       ││
│  │  └──────────────┘  └──────────────┘  └──────────────┘       ││
│  │                                                              ││
│  │  ┌──────────────────────────────────────────────────────┐   ││
│  │  │              Dynamic Agent Containers                 │   ││
│  │  │                                                       │   ││
│  │  │  ┌────────────┐  ┌────────────┐  ┌────────────┐      │   ││
│  │  │  │ agent-orch │  │ agent-w1   │  │ agent-w2   │ ...  │   ││
│  │  │  └────────────┘  └────────────┘  └────────────┘      │   ││
│  │  │                                                       │   ││
│  │  └──────────────────────────────────────────────────────┘   ││
│  │                                                              ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  Volumes:                                                        │
│  - mysql-data                                                    │
│  - redis-data                                                    │
└─────────────────────────────────────────────────────────────────┘
```

### 7.2 Agent Container 동적 생성

| 항목 | 설명 |
|------|------|
| **이미지** | `squad-agent:latest` (공통 Agent Runner 이미지) |
| **생성 시점** | 세션 시작 시 필요한 에이전트 컨테이너 생성 |
| **정리 시점** | 세션 완료/실패 시 컨테이너 제거 |
| **환경변수** | AGENT_ID, AGENT_CONFIG, REDIS_URL 등 |
| **네트워크** | squad-network에 연결 |

---

## 8. 보안 설계

### 8.1 Secret 관리

| 항목 | 방식 |
|------|------|
| 저장소 | MySQL secrets 테이블 |
| 암호화 | AES-256 |
| 복호화 키 | 환경변수 (SECRET_ENCRYPTION_KEY) |
| 참조 형식 | `ref:secret/<secret-name>` |

### 8.2 컨테이너 보안

- Agent 컨테이너는 필요한 최소 권한만 부여
- 네트워크 격리 (squad-network 내부 통신만 허용)
- API 키는 환경변수로 주입 (이미지에 포함하지 않음)

---

## 9. 개발 로드맵

### Phase 1: Core (MVP)
- [x] Platform Server 기본 구조
- [x] Agent/Squad/Session CRUD API
- [x] Agent Container 이미지 구축
- [x] Container 동적 생성/삭제
- [x] Claude LLM Provider
- [x] Redis Pub/Sub 메시징
- [x] 기본 WebSocket 모니터링
- [x] MCP Gateway 및 Built-in Tools
- [x] 단위/통합/E2E 테스트
- [ ] CLI 인터페이스 (Picocli + JLine3, `:squad-cli` 모듈)

### Phase 2: Enhancement
- [ ] OpenAI Provider 추가
- [ ] Skill 관리 고도화
- [ ] Web UI (React 관리 대시보드)
- [ ] Container 고급 관리
- [ ] API 고도화

### Phase 3: Advanced
- [ ] Gemini Provider 추가
- [ ] 직접 통신 규칙
- [ ] 메시지 흐름 시각화
- [ ] 세션 히스토리 분석
- [ ] Container 오토스케일링
