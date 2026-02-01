# Squad 프로젝트 작업 목록

이 문서는 Phase 1 MVP 구현을 위한 작업 목록입니다. 각 작업은 1개의 PR로 코드리뷰가 가능한 크기로 분리되어 있습니다.

---

## Phase 1: Core (MVP)

### 1. 프로젝트 초기 설정

| # | 작업명 | 설명 | 예상 변경 범위 | 상태 |
|---|--------|------|---------------|------|
| 1-1 | Spring Boot 프로젝트 초기화 | Gradle 설정, Java 21, 의존성 추가 (Spring Web, JPA, Validation, Redis, Docker 등) | build.gradle, application.yml | |
| 1-2 | 패키지 구조 및 공통 모듈 생성 | 패키지 구조 생성, 공통 예외 처리(GlobalExceptionHandler), API 응답 형식(ApiResponse), 공통 설정 클래스 | 10~15개 파일 | |
| 1-3 | Docker Compose 인프라 구성 | MySQL, Redis 컨테이너 설정, 네트워크 구성 | docker-compose.yml, 초기화 스크립트 | |
| 1-4 | 데이터베이스 스키마 생성 | ERD 기반 DDL 스크립트 작성 (agents, squads, sessions, messages, mcps, skills, secrets 테이블) | schema.sql | |

---

### 2. 엔티티 및 Repository 계층

| # | 작업명 | 설명 | 예상 변경 범위 | 상태 |
|---|--------|------|---------------|------|
| 2-1 | Agent 엔티티 및 Repository | Agent JPA 엔티티, AgentRepository, 기본 테스트 | 3~4개 파일 | |
| 2-2 | MCP 엔티티 및 Repository | Mcp JPA 엔티티, McpRepository, 기본 테스트 | 3~4개 파일 | |
| 2-3 | Skill 엔티티 및 Repository | Skill JPA 엔티티, SkillRepository, 기본 테스트 | 3~4개 파일 | |
| 2-4 | Squad 엔티티 및 Repository | Squad 엔티티, SquadAgent (다대다 조인 테이블), SquadRepository | 4~5개 파일 | |
| 2-5 | Session/Message 엔티티 및 Repository | Session, Message JPA 엔티티, Repository | 4~5개 파일 | |
| 2-6 | Secret 엔티티 및 암호화 서비스 | Secret 엔티티, AES256 암호화/복호화 유틸, SecretRepository | 4~5개 파일 | |

---

### 3. CRUD API 구현

| # | 작업명 | 설명 | 예상 변경 범위 | 상태 |
|---|--------|------|---------------|------|
| 3-1 | Agent CRUD API | AgentService, AgentController, DTO (Request/Response), 유효성 검증 | 5~6개 파일 | |
| 3-2 | MCP CRUD API | McpService, McpController, DTO, config JSON 검증 로직 | 5~6개 파일 | |
| 3-3 | Skill CRUD API | SkillService, SkillController, DTO | 5~6개 파일 | |
| 3-4 | Squad CRUD API | SquadService (Orchestrator 필수 검증 포함), SquadController, DTO | 6~7개 파일 | |
| 3-5 | Secret CRUD API | SecretService (암호화 저장), SecretController, 참조 해결 로직 (`ref:secret/...`) | 5~6개 파일 | |
| 3-6 | Session 기본 API | SessionService (생성/조회/취소), SessionController, DTO, 상태 관리 (PENDING, RUNNING, COMPLETED, CANCELLED) | 6~7개 파일 | |
| 3-7 | Message 조회 API | MessageService, MessageController, 세션별 메시지 조회 | 4~5개 파일 | |

---

### 4. LLM 연동

| # | 작업명 | 설명 | 예상 변경 범위 | 상태 |
|---|--------|------|---------------|------|
| 4-1 | LLM 공통 인터페이스 정의 | LlmProvider 인터페이스, LlmRequest/LlmResponse DTO, ToolCall 모델, ProviderFactory | 5~6개 파일 | |
| 4-2 | Claude LLM Provider 구현 | ClaudeProvider 구현, HTTP 클라이언트 (WebClient), 요청/응답 매핑, 에러 처리 | 4~5개 파일 | |
| 4-3 | LLM 재시도 및 Rate Limit 처리 | Exponential Backoff 재시도 로직, 429/5xx 에러 처리 | 2~3개 파일 | |
| 4-4 | Tool Use 처리 로직 | LLM 응답에서 tool_use 감지, 툴 실행 결과를 다시 LLM에 전달하는 흐름 | 3~4개 파일 | |

---

### 5. Agent Container

| # | 작업명 | 설명 | 예상 변경 범위 | 상태 |
|---|--------|------|---------------|------|
| 5-1 | Agent Container Dockerfile | squad-agent 이미지용 Dockerfile, 기본 Agent Runner 진입점 | Dockerfile, 2~3개 파일 | |
| 5-2 | Agent Runner 기본 구조 | Agent 컨테이너 내부에서 실행될 Runner 애플리케이션, 환경변수 파싱, 설정 로드 | 4~5개 파일 | |
| 5-3 | Docker Client 연동 | Docker API 클라이언트 (docker-java), 컨테이너 생성/시작/중지/삭제 | 3~4개 파일 | |
| 5-4 | Container Lifecycle Manager | 세션 시작 시 컨테이너 생성, 세션 종료 시 정리, 컨테이너 명명 규칙 (`squad-{sessionId}-{agentId}`) | 3~4개 파일 | |
| 5-5 | Container Health Check | Health 엔드포인트, 주기적 상태 확인, 비정상 컨테이너 재시작 | 3~4개 파일 | |

---

### 6. 메시징 (Redis Pub/Sub)

| # | 작업명 | 설명 | 예상 변경 범위 | 상태 |
|---|--------|------|---------------|------|
| 6-1 | Redis 연결 설정 | RedisTemplate 설정, 연결 풀 설정 | 2~3개 파일 | |
| 6-2 | Message Publisher 구현 | 세션/에이전트 채널로 메시지 발행 (`session:{id}:agent:{agentId}`) | 2~3개 파일 | |
| 6-3 | Message Subscriber 구현 | 채널 구독, 메시지 수신 리스너, Agent Runner에서 사용 | 3~4개 파일 | |
| 6-4 | 메시지 라우팅 로직 | Orchestrator↔Agent 간 메시지 라우팅, 메시지 타입별 처리 (TASK_REQUEST, TASK_RESULT 등) | 3~4개 파일 | |

---

### 7. 세션 실행 핵심 로직

| # | 작업명 | 설명 | 예상 변경 범위 | 상태 |
|---|--------|------|---------------|------|
| 7-1 | 세션 시작 흐름 구현 | 세션 생성 → Squad 로드 → Container 시작 → Orchestrator에 프롬프트 전달 | 4~5개 파일 | |
| 7-2 | Orchestrator 작업 분배 로직 | Orchestrator의 LLM 호출, 작업 분배 결정, Agent에게 태스크 전달 | 4~5개 파일 | |
| 7-3 | Worker Agent 실행 로직 | 태스크 수신 → LLM 호출 → 결과 반환 | 3~4개 파일 | |
| 7-4 | 세션 완료 처리 | Orchestrator의 완료 판단, 최종 결과 취합, 세션 상태 업데이트, Container 정리 | 3~4개 파일 | |

---

### 8. 실시간 모니터링 (WebSocket)

| # | 작업명 | 설명 | 예상 변경 범위 | 상태 |
|---|--------|------|---------------|------|
| 8-1 | WebSocket 설정 | STOMP over WebSocket 설정, 엔드포인트 (`/ws/sessions/{sessionId}`) | 2~3개 파일 | |
| 8-2 | 실시간 상태 전송 | Agent 상태 변경 시 WebSocket으로 클라이언트에 푸시 (AGENT_STATUS, MESSAGE 이벤트) | 3~4개 파일 | |
| 8-3 | 세션 완료 알림 | SESSION_COMPLETE 이벤트 전송, 연결 정리 | 2~3개 파일 | |

---

### 9. 테스트 및 문서화

| # | 작업명 | 설명 | 예상 변경 범위 | 상태 |
|---|--------|------|---------------|------|
| 9-1 | 단위 테스트 작성 | Service 계층 단위 테스트, Mock을 활용한 의존성 분리 | 테스트 파일 다수 | |
| 9-2 | API 통합 테스트 | MockMvc를 활용한 REST API 테스트, 시나리오별 테스트 | 테스트 파일 다수 | |
| 9-3 | 세션 실행 E2E 테스트 | Testcontainers로 Docker 환경 구성, 전체 플로우 테스트 | 2~3개 테스트 파일 | |

---

## 작업 순서 권장

```
1-1 → 1-2 → 1-3 → 1-4
        ↓
2-1 → 2-2 → 2-3 → 2-4 → 2-5 → 2-6
        ↓
3-1 → 3-2 → 3-3 → 3-4 → 3-5 → 3-6 → 3-7
        ↓
4-1 → 4-2 → 4-3 → 4-4
        ↓
5-1 → 5-2 → 5-3 → 5-4 → 5-5
        ↓
6-1 → 6-2 → 6-3 → 6-4
        ↓
7-1 → 7-2 → 7-3 → 7-4
        ↓
8-1 → 8-2 → 8-3
        ↓
9-1 → 9-2 → 9-3
```

---

## 요약

| 카테고리 | 작업 수 |
|----------|---------|
| 프로젝트 초기 설정 | 4개 |
| 엔티티/Repository | 6개 |
| CRUD API | 7개 |
| LLM 연동 | 4개 |
| Agent Container | 5개 |
| 메시징 | 4개 |
| 세션 실행 | 4개 |
| 실시간 모니터링 | 3개 |
| 테스트 | 3개 |
| **총계** | **40개** |

---

## 상태 표기법

- (빈칸): 미시작
- `진행중`: 현재 작업 중
- `완료`: 작업 완료
- `보류`: 블로커로 인해 보류

---

## 참고 문서

- [SPEC.md](./SPEC.md): 프로젝트 명세
- [ARCHITECTURE.md](./ARCHITECTURE.md): 시스템 아키텍처
- [USE_CASES.md](./USE_CASES.md): 유스케이스 정의
- [LLM_INTEGRATION.md](./LLM_INTEGRATION.md): LLM 연동 가이드
