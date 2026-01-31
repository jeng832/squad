# Squad 시스템 아키텍처 설계서

## 1. 개요

### 1.1 문서 목적
Squad 플랫폼의 전체 시스템 아키텍처와 주요 모듈 구조를 정의합니다.

### 1.2 시스템 개요
Squad는 멀티 AI 에이전트 협업 플랫폼으로, 여러 AI 에이전트가 Orchestrator의 조율 하에 복잡한 작업을 수행합니다. 각 에이전트는 독립적인 Docker 컨테이너로 실행되어 확장성과 격리성을 보장합니다.

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

---

## 2. 시스템 전체 아키텍처

### 2.1 High-Level 아키텍처

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                 Client                                       │
│                    Web UI (React) / REST API Clients                         │
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
│  │ │ MCP Client  │ │  │ │ MCP Client  │ │  │ │ MCP Client  │ │              │
│  │ └─────────────┘ │  │ └─────────────┘ │  │ └─────────────┘ │              │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘              │
│           │                    │                    │                        │
│           └────────────────────┼────────────────────┘                        │
│                                │                                             │
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
| **Squad Platform Server** | 에이전트/Squad/세션 관리, API 제공, 모니터링 |
| **Agent Container** | 개별 에이전트 실행 환경, LLM/MCP 연동 |
| **MySQL** | 에이전트, Squad, 세션, 메시지 등 영속 데이터 저장 |
| **Redis** | 에이전트 간 메시지 전달 (Pub/Sub), 실시간 상태 공유 |
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
│         ┌───────────────┼───────────────┐            │
│         ▼               ▼               ▼            │
│  ┌───────────┐   ┌───────────┐   ┌───────────┐      │
│  │LLM Client │   │MCP Client │   │ Message   │      │
│  │           │   │           │   │ Handler   │      │
│  │- Claude   │   │- GitHub   │   │           │      │
│  │- OpenAI   │   │- File     │   │- Redis    │      │
│  │- Gemini   │   │- Custom   │   │  Pub/Sub  │      │
│  └───────────┘   └───────────┘   └───────────┘      │
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

**통신 채널 구조:**
- `session:{sessionId}:orchestrator` - Orchestrator 전용 채널
- `session:{sessionId}:agent:{agentId}` - 개별 Agent 채널
- `session:{sessionId}:broadcast` - 전체 브로드캐스트

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

### 4.2 Agent Container 모듈

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
│  │                    MCP Integration                          │ │
│  │  McpConnector │ ToolExecutor │ ProcessManager              │ │
│  └────────────────────────────────────────────────────────────┘ │
│                              │                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                   Message Handler                           │ │
│  │  RedisSubscriber │ MessageRouter │ ResponseSender          │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. 데이터 모델

### 5.1 ERD

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
- [ ] Platform Server 기본 구조
- [ ] Agent/Squad/Session CRUD API
- [ ] Agent Container 이미지 구축
- [ ] Container 동적 생성/삭제
- [ ] Claude LLM Provider
- [ ] Redis Pub/Sub 메시징
- [ ] 기본 WebSocket 모니터링

### Phase 2: Enhancement
- [ ] OpenAI Provider 추가
- [ ] MCP 연동
- [ ] Skill 관리
- [ ] 상세 모니터링 대시보드
- [ ] Container 헬스체크

### Phase 3: Advanced
- [ ] Gemini Provider 추가
- [ ] 직접 통신 규칙
- [ ] 메시지 흐름 시각화
- [ ] 세션 히스토리 분석
- [ ] Container 오토스케일링
