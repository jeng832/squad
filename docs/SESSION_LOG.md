# 세션 로그

## 2026-02-10

### 작업 내용
- **7-4: 세션 완료 처리 로직 구현** ([PR #47](https://github.com/jeng832/squad/pull/47))
  - `SessionCompleteHandler`: 콜백 인터페이스 도입 (순환 의존 방지)
  - `OrchestratorService.completeSession()`: 핸들러 호출로 세션 완료 위임
  - `SessionExecutionService.complete()`: 세션 COMPLETED 전이, Worker 구독 정리, Container 정리
  - `TransactionTemplate` 적용: 콜백에서의 self-invocation 시 트랜잭션 보장 (codex-cli 리뷰 반영)
  - Container 정리 시 `buildContainerName()`으로 이름 재구성
  - 단위 테스트 추가: OrchestratorServiceTest 1개, SessionExecutionServiceTest 4개
  - codex-cli 리뷰: P1 1건 (Spring AOP 프록시 우회) → TransactionTemplate으로 해결
  - **이슈 #9 완료** (Closes #9)

- **7-3: Worker Agent 실행 로직 구현** ([PR #46](https://github.com/jeng832/squad/pull/46))
  - `WorkerContext`: Worker Agent별 실행 상태 관리 (CopyOnWriteArrayList, volatile Subscription)
  - `WorkerService`: 태스크 수신 → LLM 호출 → 결과 반환 전체 흐름
    - startWorker/stopWorker/stopAllWorkers lifecycle 관리
    - TASK_REQUEST 수신 시 LLM 호출 후 TASK_RESULT 반환
    - LLM 호출 실패 시 에러 결과를 Orchestrator에게 전달 (sendErrorResult)
  - `SessionExecutionService` 통합: Worker 구독 → Orchestrator 구독 → 프롬프트 발행 순서 보장
  - 단위 테스트 9개 작성 (WorkerServiceTest)
  - codex-cli 리뷰: P2 1건 (세션 완료 시 Worker 정리) → 7-4 범위로 기록

- **7-2: Orchestrator 작업 분배 로직 구현** ([PR #45](https://github.com/jeng832/squad/pull/45))
  - `OrchestrationContext`: 세션별 Orchestration 상태 관리 (스레드 안전)
    - `CopyOnWriteArrayList`로 대화 히스토리, `AtomicInteger`로 대기 작업 수 관리
    - `synchronized` list로 구독 관리, `unsubscribeAll()`로 일괄 해제
  - `OrchestratorService`: LLM 호출 및 tool_use 기반 작업 분배
    - Agent 채널 + Orchestrator 채널 이중 구독
    - TASK_REQUEST 수신 → LLM 호출 → delegate_task/complete_session tool_use 해석
    - TASK_RESULT 수신 → 대기 작업 차감 → 모든 작업 완료 시 LLM 재호출
    - `ConcurrentHashMap`으로 활성 Orchestration 관리
  - `SessionExecutionService` 통합
    - `startOrchestration()` 호출 후 `sendPromptToOrchestrator()` (구독 먼저, 발행 나중)
    - catch 블록에 `stopOrchestration()` 추가하여 실패 시 구독 정리
  - 단위 테스트 8개 작성 (OrchestratorServiceTest)
    - 구독 설정/해제, TASK_REQUEST→delegate, TASK_RESULT→LLM 재호출, complete_session 브로드캐스트
    - 시스템 프롬프트 내용 검증, 도구 정의 검증, Provider 미존재 처리, 대기 작업 잔여 시 LLM 미호출

### 주요 결정사항
- 구독 설정 → 프롬프트 발행 순서 보장: Redis Pub/Sub 메시지 유실 방지
- 대기 작업 카운터(`AtomicInteger`) 기반 LLM 재호출 타이밍 결정

- **7-1: 세션 시작 흐름 구현** ([PR #44](https://github.com/jeng832/squad/pull/44))
  - `SessionExecutionService` 구현: 세션 시작 lifecycle 조율
    - PENDING 상태 검증 → Container 생성/시작 → RUNNING 상태 전이 → Orchestrator에 프롬프트 전달
    - Container 시작 실패 및 DB flush 실패 시 cleanup 로직 포함
  - `SessionController`에 `POST /api/v1/sessions/{id}/start` 엔드포인트 추가
  - `SessionExecutionServiceTest` 6개 단위 테스트 작성
  - `SessionControllerTest`에 start 관련 3개 테스트 추가

### 주요 결정사항
- Container 생성은 `@Transactional` 내부에서 수행하되, flush 시점을 명시적으로 관리하여 DB 실패 시 Container 정리 가능하도록 설계
- Orchestrator Container를 먼저 시작한 후 Agent Container를 순차적으로 시작

---

## 2025-01-31

### 작업 내용
- SPEC.md 검토 및 수정
  - Java 17+ → Java 21로 변경 (Virtual Threads 등 이점 활용)
  - Secret Store: MySQL 방식으로 결정 (secrets 테이블, AES256 암호화, 복호화 키는 환경변수 관리)
- 프로젝트 문서 구조 정립
  - `CLAUDE.md` 생성: Claude 작업 지침
  - `docs/SESSION_LOG.md` 생성: 세션별 작업 기록
  - 역할 분리: SPEC.md(명세) vs CLAUDE.md(작업 지침)

---

## 2026-01-31

### 작업 내용
- **USE_CASES.md 작성**: 사용자 Use Case 정의
  - Actor 정의 (User, Admin, Orchestrator Agent, Worker Agent)
  - 20개 Use Case 정의 (에이전트/MCP/Skill/Squad/세션 관리)
  - 사용 시나리오 작성 (멀티 레포 코드 리뷰, 기획서 기반 개발)
  - MVP 우선순위 정의

- **LLM_INTEGRATION.md 작성**: LLM API 조사 및 MVP 결정
  - Claude API (Anthropic): Messages API, Tool Use
  - OpenAI API: Chat Completions API, Function Calling
  - Google Gemini API: Interactions API
  - Java 호출 예시 코드 작성
  - **MVP LLM 결정: Anthropic Claude** (코딩 특화, 간결한 API, Tool Use 지원)
  - 공통 LLM Provider 인터페이스 설계

- **ARCHITECTURE.md 작성**: 전체 아키텍처 및 모듈 설계
  - High-Level 시스템 아키텍처
  - 패키지 구조 정의
  - 핵심 모듈 상세 설계 (Agent, Squad, Session, LLM Provider, MCP, Message)
  - ERD 및 테이블 정의
  - REST API 엔드포인트 설계
  - WebSocket API 설계
  - Docker Compose 구성
  - MVP 개발 계획 (Phase 1/2/3)

---

## 2026-02-01

### 작업 내용
- **샌드박스 및 격리 구조 문서화**: Agent Container의 샌드박스 환경과 Session 격리 구조를 모든 관련 문서에 업데이트
  - **SPEC.md**:
    - 용어 정의에 Sandbox, Workspace 추가
    - 섹션 6.5 "격리 정책" 추가 (Agent 샌드박스 격리, Session 격리, 동시 작업 지원, Workspace 정리 정책)
  - **ARCHITECTURE.md**:
    - 섹션 1.2 시스템 개요에 격리 원칙 추가
    - 섹션 3.4 "샌드박스 및 Workspace 구조" 추가
    - 섹션 3.5 "동시 세션 실행 아키텍처" 추가 (다이어그램 포함)
    - 섹션 5.6 "Container 생명주기 관리"에 Workspace 관리 내용 추가
    - 섹션 5.7 "Workspace 생명주기 관리" 추가
  - **USE_CASES.md**:
    - UC-014 세션 시작에 동시 세션 실행 관련 설명 추가

- **용어 정의 정립**: Squad 관련 용어 명확화
  - **Squad Template**: 에이전트들의 팀 구성 템플릿 (기존 Squad 정의)
  - **Active Squad**: Session을 수행 중인 Squad 인스턴스
  - **Session**: 사용자의 작업 요청부터 결과 반환까지의 수행 단위 (정의 명확화)
  - 모든 관련 문서(SPEC.md, ARCHITECTURE.md, USE_CASES.md)에 용어 일관성 적용

- **핵심 설계 결정**:
  - 모든 Agent는 독립적인 샌드박스(Docker Container)에서 동작
  - 각 Active Squad의 Agent는 자신만의 Workspace(/workspace) 보유
  - Git Repository는 Agent별로 독립적으로 clone
  - 동일 Squad Template으로 여러 Session 동시 실행 가능 (각각 독립적인 Active Squad 생성)
  - Session 완료 시 Workspace 정리 (실패 시 일정 기간 보존)

---

## 2026-02-02

### 작업 내용
- **작업 1-1: Spring Boot 프로젝트 초기화** ([PR #14](https://github.com/jeng832/squad/pull/14))
  - `build.gradle`: Spring Boot 3.4.2, Java 21, 의존성 설정
    - Spring Boot Starters: Web, Data JPA, Data Redis, Validation, WebSocket, Actuator
    - MySQL Connector, Lombok, Jackson, Docker Java Client, WebFlux
    - 테스트: JUnit 5, Testcontainers, H2 Database
  - `settings.gradle`: Gradle Toolchain 자동 프로비저닝 설정
  - `application.yml`: 데이터베이스, Redis, JPA, Jackson, 로깅, 커스텀 설정
  - `SquadApplication.java`: 메인 애플리케이션 클래스
  - 테스트 환경 설정 (H2 인메모리 DB)
  - 브랜치: `feature/1-1-spring-boot-init`
  - 이슈 [#3](https://github.com/jeng832/squad/issues/3)에 PR 링크 코멘트 추가
  - TASKS.md 상태 업데이트

- **작업 1-2: 패키지 구조 및 공통 모듈 생성** ([PR #15](https://github.com/jeng832/squad/pull/15))
  - 패키지 구조: `common.api`, `common.exception`, `common.config`
  - `ApiResponse<T>`: 모든 REST API 응답을 감싸는 공통 래퍼 클래스 (성공/에러 팩토리 메서드)
  - `ErrorCode`: HTTP 상태 코드·기본 메시지를 포함하는 전역 에러 코드 열거형
  - 예외 계층: `SquadException` (기본) → `NotFoundException` (404), `ValidationException` (400)
  - `GlobalExceptionHandler`: `@RestControllerAdvice`로 SquadException, Bean Validation, 405, 일반 Exception을 통일 처리
  - `RedisConfig`: `RedisTemplate<String, Object>`를 Jackson 직렬화기로 구성
  - `WebConfig`: CORS 설정 (`/api/**`)
  - 테스트: `ApiResponseTest`, `SquadExceptionTest`, `GlobalExceptionHandlerTest` (`@WebMvcTest`)
  - 이슈 [#3](https://github.com/jeng832/squad/issues/3)에 PR 링크 코멘트 추가

---

## 2026-02-03

### 작업 내용
- **작업 1-3: Docker Compose 인프라 구성** ([PR #16](https://github.com/jeng832/squad/pull/16))
  - `docker-compose.yml`: MySQL 8.0, Redis 7 컨테이너 설정
  - `docker/mysql/init.sql`: DB 초기화 스크립트 (squad, squad_test DB)
  - squad-network 브릿지 네트워크
  - 볼륨: mysql-data, redis-data

- **작업 1-4: 데이터베이스 스키마 생성** ([PR #17](https://github.com/jeng832/squad/pull/17))
  - `src/main/resources/schema.sql`: ERD 기반 DDL 스크립트 작성 (10개 테이블)
    - 독립 테이블: `agents`, `mcps`, `skills`, `secrets`
    - 관계 테이블: `squads` (orchestrator FK), `sessions` (squad FK), `messages` (session FK)
    - 다대다 조인 테이블: `squad_agents`, `agent_mcps`, `agent_skills` (복합 PK + CASCADE DELETE)
  - JSON 컬럼 활용: `llm_config`, `config`, `direct_communication`, `required_mcps`
  - `messages.from_agent_id` / `to_agent_id`: 시스템 메시지 시 NULL 가능 → FK 제약 없음
  - 인덱스: `idx_sessions_status`, `idx_messages_session_created`
  - `application.yml`: `spring.sql.init.mode: always` 추가
  - `test/application.yml`: `spring.sql.init.mode: never` (H2 create-drop과 충돌 방지)
  - 이슈 [#3](https://github.com/jeng832/squad/issues/3)에 PR 링크 코멘트 추가

- **작업 2-1: Agent 엔티티 및 Repository** ([PR #18](https://github.com/jeng832/squad/pull/18))
  - `RoleType` 열거형: ORCHESTRATOR, WORKER, ANALYST, SCRIBE, CUSTOM
  - `Agent` 엔티티: `agents` 테이블 매핑
    - `llm_config` JSON 컬럼 → Hibernate 6 `@JdbcTypeCode(SqlTypes.JSON)` + Jackson 직렬화
    - `role_type` ENUM → 내부 `RoleTypeConverter`로 대문자 ENUM ↔ DB 소문자 ENUM 변환
    - `@PrePersist` / `@PreUpdate`로 타임스탬프 관리, `update()` 메서드로 정보 수정 지원
  - `AgentRepository`: `JpaRepository<Agent, Long>` 기본 CRUD
  - `AgentRepositoryTest`: `@DataJpaTest` 기반 6가지 테스트 시나리오
  - 이슈 [#4](https://github.com/jeng832/squad/issues/4)에 PR 링크 코멘트 추가

- **작업 2-2: MCP 엔티티 및 Repository** ([PR #19](https://github.com/jeng832/squad/pull/19))
  - `Mcp` 엔티티: `mcps` 테이블 매핑
    - `name` UNIQUE 제약조건, `description` (nullable), `config` JSON 컬럼
    - `@JdbcTypeCode(SqlTypes.JSON)`으로 Hibernate 6 JSON 직렬화
    - `@PrePersist` / `@PreUpdate`로 타임스탬프 관리, `update()` 메서드로 정보 수정 지원
  - `McpRepository`: `JpaRepository<Mcp, Long>` 기본 CRUD + `findByName` 조회
  - `McpRepositoryTest`: `@DataJpaTest` 기반 8가지 테스트 시나리오
  - 이슈 [#4](https://github.com/jeng832/squad/issues/4)에 PR 링크 코멘트 추가

- **작업 2-3: Skill 엔티티 및 Repository** ([PR #20](https://github.com/jeng832/squad/pull/20))
  - `Skill` 엔티티: `skills` 테이블 매핑
    - `name` UNIQUE 제약조건, `description` (nullable), `prompt` (TEXT NOT NULL)
    - `required_mcps` JSON 컬럼 (`List<Long>`, nullable): 필요한 MCP ID 목록
    - `@JdbcTypeCode(SqlTypes.JSON)`으로 Hibernate 6 JSON 직렬화
    - `@PrePersist` / `@PreUpdate`로 타임스탬프 관리, `update()` 메서드로 정보 수정 지원
  - `SkillRepository`: `JpaRepository<Skill, Long>` 기본 CRUD + `findByName` 조회
  - `SkillRepositoryTest`: `@DataJpaTest` 기반 8가지 테스트 시나리오
  - 이슈 [#4](https://github.com/jeng832/squad/issues/4)에 PR 링크 코멘트 추가

- **작업 2-4: Squad 엔티티 및 Repository** ([PR #21](https://github.com/jeng832/squad/pull/21))
  - `Squad` 엔티티: `squads` 테이블 매핑
    - `orchestrator` → Agent와의 `@ManyToOne` 관계 (`orchestrator_id` FK)
    - `agents` → Agent와의 `@ManyToMany` 관계 (`squad_agents` 조인 테이블, `@Builder.Default` Set)
    - `direct_communication` JSON 컬럼 (nullable): 직접 통신 규칙 저장
    - `addAgent()` / `removeAgent(Long agentId)` 메서드로 멤버 관리
    - `update()` 메서드로 Squad 정보 수정 지원
  - `SquadRepository`: `JpaRepository<Squad, Long>` 기본 CRUD + `findByOrchestraterId` 조회
  - `SquadRepositoryTest`: `@DataJpaTest` 기반 8가지 테스트 시나리오 (ManyToOne, ManyToMany, JSON 포함)
  - 이슈 [#4](https://github.com/jeng832/squad/issues/4)에 PR 링크 코멘트 추가

- **작업 2-5: Session/Message 엔티티 및 Repository** ([PR #22](https://github.com/jeng832/squad/pull/22))
  - `SessionStatus` 열거형: PENDING, RUNNING, COMPLETED, CANCELLED
  - `MessageType` 열거형: TASK_REQUEST, TASK_RESULT, HELP_REQUEST, HELP_RESPONSE, SYSTEM
  - `Session` 엔티티: `sessions` 테이블 매핑
    - Squad와의 `@ManyToOne` 관계 (`squad_id` FK)
    - 상태 전이 메서드: `start()`, `complete(result)`, `cancel()`
    - `updated_at` 없음 (상태 변경은 status/started_at/completed_at로 추적)
  - `Message` 엔티티: `messages` 테이블 매핑 (append-only)
    - Session과의 `@ManyToOne` 관계 (`session_id` FK)
    - `fromAgentId` / `toAgentId`: 시스템 메시지 시 null 가능
  - `SessionRepository`: `findBySquadId`, `findByStatus`
  - `MessageRepository`: `findBySessionIdOrderByCreatedAt`, `findBySessionIdAndType`
  - `SessionRepositoryTest`: 7가지 테스트, `MessageRepositoryTest`: 4가지 테스트
  - 이슈 [#4](https://github.com/jeng832/squad/issues/4)에 PR 링크 코멘트 추가

- **작업 2-6: Secret 엔티티 및 암호화 서비스** ([PR #23](https://github.com/jeng832/squad/pull/23))
  - `Secret` 엔티티: `secrets` 테이블 매핑
    - `name` UNIQUE 제약조건, `value` (암호화된 값 저장), `update(value)` 메서드
  - `SecretRepository`: `JpaRepository<Secret, Long>` 기본 CRUD + `findByName` 조회
  - `AesEncryptionUtil`: AES-256 암호화/복호화 유틸리티
    - 키 유도: passphrase → SHA-256 해싱 → 32바이트 키
    - 알고리즘: AES/CBC/PKCS5Padding, 16바이트 랜덤 IV prepend
    - 저장 형식: Base64(IV + 암호화된 바이트열)
  - `SecretRepositoryTest`: 5가지 테스트 (CRUD, UNIQUE 제약)
  - `AesEncryptionUtilTest`: 6가지 테스트 (라운드트립, 랜덤 IV, 유니코드, 잘못된 키/암호문)
  - 이슈 [#4](https://github.com/jeng832/squad/issues/4)에 PR 링크 코멘트 추가

---

## 2026-02-04

### 작업 내용
- **작업 3-1: Agent CRUD API** ([PR #24](https://github.com/jeng832/squad/pull/24))
  - `AgentCreateRequest` DTO: `name` (@NotBlank, @Size(max=100)), `roleType` (@NotNull), `role` (@NotBlank), `llmConfig` (@NotNull)
  - `AgentUpdateRequest` DTO: `name`, `role`, `llmConfig` (roleType 수정 불가)
  - `AgentResponse` DTO: 엔티티 → 응답 변환 (정적 팩토리 `from(Agent)`)
  - `AgentService`: CRUD 비즈니스 로직
    - `findAll()`, `findById()` — 조회 시 NotFoundException 발생
    - `create()` — Agent 빌더로 엔티티 생성 후 저장
    - `update()` — `agent.update()` 메서드 호출로 수정
    - `delete()` — 존재 여부 확인 후 삭제
    - `@Transactional(readOnly = true)` 기본, 변경 작업은 `@Transactional` 오버라이드
  - `AgentController`: `/api/v1/agents` 기반 REST 엔드포인트
    - GET (목록), POST (생성, 201), GET/{id} (상세), PUT/{id} (수정), DELETE/{id} (삭제)
    - `@Valid` + `@RequestBody`로 Jakarta Bean Validation 적용
    - 생성·삭제 시 성공 메시지 포함
  - `AgentControllerTest`: `@WebMvcTest` + MockMvc 기반 10가지 테스트
    - 목록 조회, ID 조회, 404, 생성(201), name 빈값 검증(400), roleType null 검증(400), 수정, 수정 404, 삭제, 삭제 404
  - `SquadException.getErrorCode()` 메서드 추가 (GlobalExceptionHandler 호환 수정)
  - 이슈 [#5](https://github.com/jeng832/squad/issues/5)에 PR 링크 코멘트 추가

- **작업 3-2: MCP CRUD API** ([PR #25](https://github.com/jeng832/squad/pull/25))
  - `McpCreateRequest` DTO: `name` (@NotBlank, @Size(max=100)), `description` (nullable), `config` (@NotNull)
  - `McpUpdateRequest` DTO: `name`, `description`, `config` (동일 검증)
  - `McpResponse` DTO: 엔티티 → 응답 변환 (정적 팩토리 `from(Mcp)`)
  - `McpService`: CRUD 비즈니스 로직
    - `findAll()`, `findById()` — 조회 시 NotFoundException 발생
    - `create()`, `update()` — `validateConfig()` 호출 후 저장/수정
    - `validateConfig()`: `config` 맵에 `command` 키 존재 여부 검증, 누락 시 ValidationException 발생
    - `delete()` — 존재 여부 확인 후 삭제
  - `McpController`: `/api/v1/mcps` 기반 REST 엔드포인트
    - GET (목록), POST (생성, 201), GET/{id} (상세), PUT/{id} (수정), DELETE/{id} (삭제)
  - `McpControllerTest`: `@WebMvcTest` + MockMvc 기반 12가지 테스트
    - 목록 조회, ID 조회, 404, 생성(201), name 빈값(400), config command 누락(400), config null(400), 수정, 수정 404, 수정 command 누락(400), 삭제, 삭제 404
  - 이슈 [#5](https://github.com/jeng832/squad/issues/5)에 PR 링크 코멘트 추가

- **작업 3-3: Skill CRUD API** ([PR #26](https://github.com/jeng832/squad/pull/26))
  - `SkillCreateRequest` DTO: `name` (@NotBlank, @Size(max=100)), `description` (nullable), `prompt` (@NotBlank), `requiredMcps` (List<Long>, nullable)
  - `SkillUpdateRequest` DTO: 동일 구조
  - `SkillResponse` DTO: 엔티티 → 응답 변환 (정적 팩토리 `from(Skill)`)
  - `SkillService`: CRUD 비즈니스 로직, NotFoundException 발생
  - `SkillController`: `/api/v1/skills` 기반 REST 엔드포인트
  - `SkillControllerTest`: `@WebMvcTest` + MockMvc 기반 11가지 테스트
    - 목록 조회, ID 조회, 404, 생성(201), requiredMcps 없이 생성, name 빈값(400), prompt 빈값(400), 수정, 수정 404, 삭제, 삭제 404
  - 이슈 [#5](https://github.com/jeng832/squad/issues/5)에 PR 링크 코멘트 추가

- **작업 3-4: Squad CRUD API** ([PR #27](https://github.com/jeng832/squad/pull/27))
  - `SquadCreateRequest` DTO: `name` (@NotBlank), `description` (nullable), `orchestratorId` (@NotNull), `agentIds` (List<Long>, nullable), `directCommunication` (Map, nullable)
  - `SquadUpdateRequest` DTO: `name`, `description`, `agentIds`, `directCommunication` (orchestrator 수정 불가)
  - `SquadResponse` DTO: `orchestratorId`, `agentIds` (Set<Long>)로 관계 ID만 반환
  - `SquadService`: CRUD 비즈니스 로직
    - `validateOrchestrator()`: orchestrator Agent 존재 확인 + `roleType == ORCHESTRATOR` 검증
    - `create()`: orchestrator 검증 후 Squad 저장, agentIds로 멤버 Agent를 루프 조회 후 `addAgent()`
    - `update()`: `agentIds` 제공 시 `agents.clear()` + 재추가로 재구성
    - Agent 미존재 시 `AGENT_NOT_FOUND`, roleType 불일치 시 `INVALID_ORCHESTRATOR_ROLE` 발생
  - `SquadController`: `/api/v1/squads` 기반 REST 엔드포인트
  - `SquadControllerTest`: `@WebMvcTest` + MockMvc 기반 12가지 테스트
    - 목록 조회, ID 조회, 404, 생성(201), orchestratorId null(400), orchestrator 미존재(404), roleType 불일치(400), agentIds 없이 생성(201), 수정, 수정 404, 삭제, 삭제 404
  - 이슈 [#5](https://github.com/jeng832/squad/issues/5)에 PR 링크 코멘트 추가

- **작업 3-5: Secret CRUD API** ([PR #28](https://github.com/jeng832/squad/pull/28))
  - `SecretCreateRequest` DTO: `name` (@NotBlank, @Size(max=100)), `value` (@NotBlank)
  - `SecretUpdateRequest` DTO: `value` (@NotBlank)
  - `SecretResponse` DTO: `value` 제외 (민감 정보 보호) — `id`, `name`, `createdAt`, `updatedAt`만 반환
  - `SecretService`: CRUD 비즈니스 로직 + 참조 해결
    - `create()`/`update()`: `AesEncryptionUtil.encrypt()`로 암호화 후 저장
    - `resolveSecret(ref)`: `ref:secret/<name>` 형식 파싱 → 이름으로 조회 → `decrypt()` 복호화 반환
    - ref 형식 불일치 시 `INVALID_REQUEST` (400), name 빈값 시 `INVALID_REQUEST` (400), 미존재 시 `SECRET_NOT_FOUND` (404)
  - `SecretController`: `/api/v1/secrets` 기반 REST 엔드포인트
    - `GET /api/v1/secrets/resolve?ref=ref:secret/<name>` 참조 해결 엔드포인트 추가
  - `SecretControllerTest`: `@WebMvcTest` + MockMvc 기반 13가지 테스트
    - 목록 조회(value 미포함), ID 조회(value 미포함), 404, 생성(201, value 미포함), name 빈값(400), value 빈값(400), 수정, 수정 404, 삭제, 삭제 404, 참조 해결 성공, 참조 해결 미존재(404), 참조 해결 잘못된 형식(400)
  - 이슈 [#5](https://github.com/jeng832/squad/issues/5)에 PR 링크 코멘트 추가

- **작업 3-6: Session 기본 API**
  - SessionService, SessionController, DTO(생성/응답) 구현: 세션 생성, 목록/단건 조회, 취소 처리 (상태 PENDING/RUNNING/COMPLETED/CANCELLED)
  - Squad 존재 검증, 완료/취소 상태 세션 재취소 시 `INVALID_SESSION_STATE` 반환
  - WebMvcTest 기반 SessionController 테스트 추가 (정상/검증 실패/404/취소 시나리오)

- **작업 3-7: Message 조회 API**
  - MessageService, MessageController 구현: `GET /api/v1/sessions/{id}/messages`로 세션 메시지 조회, `type` 파라미터로 MessageType별 필터 지원
  - Session 존재 검증 후 메시지를 생성 시각 순으로 반환
  - MessageResponse DTO 추가
  - WebMvcTest 기반 MessageController 테스트 추가 (전체/타입별 조회, 세션 미존재 404)

---

## 2026-02-08

### 작업 내용
- **작업 5-1: Agent Container Dockerfile**
  - `docker/agent/Dockerfile`: squad-agent 이미지용 기본 Dockerfile 추가
  - `docker/agent/entrypoint.sh`: agent-runner.jar 실행용 엔트리포인트 추가
  - [PR #35](https://github.com/jeng832/squad/pull/35)

- **작업 5-2: Agent Runner 기본 구조**
  - `AgentRunnerApplication`: Agent Runner 진입점 추가
  - `AgentConfig`/`AgentConfigLoader`: AGENT_CONFIG 파싱 및 AGENT_ID override 지원
  - 기본 파싱 테스트 추가
  - [PR #36](https://github.com/jeng832/squad/pull/36)

- **작업 5-3: Docker Client 연동**
  - DockerClient 빈 설정 추가 및 docker host 구성 지원
  - 컨테이너 조회 유틸(`DockerContainerManager`) 추가
  - Docker 조회 로직 테스트 추가
  - [PR #37](https://github.com/jeng832/squad/pull/37)

- **작업 5-4: Container Lifecycle Manager**
  - 컨테이너 생성/시작/중지/삭제를 담당하는 Lifecycle Manager 추가
  - 컨테이너 명명 규칙(`squad-{sessionId}-{agentId}`) 적용
  - 기본 동작 테스트 추가
  - [PR #38](https://github.com/jeng832/squad/pull/38)

- **작업 5-5: Container Health Check**
  - `/health` 엔드포인트 추가
  - 주기적 상태 점검 및 비정상 컨테이너 재시작 로직 추가
  - 스케줄러 활성화 설정 추가
  - Health Checker 테스트 추가
  - [PR #39](https://github.com/jeng832/squad/pull/39)

---

## 2026-02-09

### 작업 내용
- **작업 6-1: Redis Pub/Sub 연결 설정**
  - `RedisMessageConfig`: Pub/Sub 전용 설정 클래스 추가
    - `RedisMessageListenerContainer` 빈: 동적 채널 구독/해제 관리
    - `messageSerializer` 빈: Jackson 기반 JSON 직렬화기
  - `RedisChannelConstants`: 채널 네이밍 규칙 유틸리티
    - `session:{sessionId}:orchestrator` - Orchestrator 전용 채널
    - `session:{sessionId}:agent:{agentId}` - 개별 Agent 채널
    - `session:{sessionId}:broadcast` - 전체 브로드캐스트 채널
    - `session:{sessionId}:*` - 세션 패턴
  - `RedisTestContainerConfig`: Testcontainers Redis 공통 설정 (이후 6-2~6-4, 10-3 재활용)
  - `RedisChannelConstantsTest`: 채널 네이밍 검증 6가지 테스트
  - `RedisMessageConfigTest`: Testcontainers Redis 기반 통합 테스트 5가지
    - 빈 로딩, 직렬화 라운드트립, Redis ping, Pub/Sub 발행/구독 라운드트립
  - [PR #40](https://github.com/jeng832/squad/pull/40)

- **작업 6-2: Message Publisher 구현 및 인터페이스 추상화**
  - codex-cli와 설계 논의 및 코드 리뷰를 통해 구조 개선
  - `MessagePublisher`: 인터페이스로 분리 (도메인 포트)
    - `sendToAgent(message)`, `sendToOrchestrator(message)`, `broadcast(message)`
    - 라우팅 정보를 SessionMessage 단일 소스로 통일
  - `RedisMessagePublisher`: Redis Pub/Sub 구현체
    - `@ConditionalOnProperty(name="squad.messaging.provider", havingValue="redis")` 적용
    - 필수 필드(sessionId, toAgentId) 검증 추가
  - `SessionMessage`: 메시지 DTO (Redis 참조 제거, 순수 도메인 DTO화)
  - Redis 관련 클래스를 `messaging.redis` 패키지로 이동
    - `RedisChannelConstants`: package-private으로 접근 범위 축소
    - `RedisMessageConfig`: `@ConditionalOnProperty` 적용 (`matchIfMissing=false`)
  - 테스트 안정성 개선
    - `AwaitableMessageListener`: `SubscriptionListener` 기반 구독 확정 대기 (Thread.sleep 제거)
    - 모든 테스트에 `try/finally` 리스너 해제 보장
    - `RedisMessageProviderDisabledTest`: provider 비활성화 시 빈 미생성 검증
    - `RedisMessagePublisherTest`: 7가지 테스트 (발행/수신, 타입 보존, 세션 격리, null 검증)
  - ARCHITECTURE.md에 메시징 추상화 구조 반영
  - **보류 작업 → 6-3에서 수행**: messageSerializer 빈과 Subscriber 역직렬화 경로 정리
  - [PR #41](https://github.com/jeng832/squad/pull/41)

- **작업 6-3: Message Subscriber 구현**
  - codex-cli와 설계 논의 및 코드 리뷰를 통해 구현
  - `Subscription`: 구독 lifecycle 관리 인터페이스 (unsubscribe만, 멱등)
  - `MessageHandler`: @FunctionalInterface (도메인 의미 명확화, Consumer 대신 채택 - codex와 합의)
  - `MessageSubscriber`: 도메인 포트 인터페이스
    - `subscribeToAgent(sessionId, agentId, handler)` → Subscription
    - `subscribeToOrchestrator(sessionId, handler)` → Subscription
    - `subscribeToBroadcast(sessionId, handler)` → Subscription
  - `RedisMessageSubscriber`: Redis Pub/Sub 구현체
    - `@ConditionalOnProperty(name="squad.messaging.provider", havingValue="redis")` 적용
    - `messageSerializer`로 1차 역직렬화 후 `ObjectMapper.convertValue` fallback (private 생성자 대응)
    - `AtomicBoolean` 기반 멱등한 구독 해제, 실패 시 active 상태 복원
    - null 역직렬화 가드 (log+drop)
    - Assert.notNull 파라미터 검증 (RedisMessagePublisher와 동일 패턴)
  - codex-cli 리뷰 6건 중 5건 수정, 1건(AutoCloseable) 보류 합의
    - Thread.sleep → retry-publish 패턴 (`publishUntilReceived`) 으로 테스트 안정성 개선
    - AutoCloseable 보류: 구독은 long-lived이므로 try-with-resources 유도 부적절
  - 통합 테스트 15개 (구독/수신, 해제, 멱등성, 세션 격리, null 검증 7개, 비활성화)
  - 6-2 보류 항목 해소: messageSerializer 빈과 Subscriber 역직렬화 경로 정리 완료
  - [PR #42](https://github.com/jeng832/squad/pull/42)

- **작업 6-4: 메시지 라우팅 로직**
  - `MessageRouter`: 메시지 타입 기반 라우팅 인터페이스
  - `DefaultMessageRouter`: MessageType → 채널 라우팅 구현
    - TASK_REQUEST, HELP_RESPONSE → `sendToAgent` (toAgentId 필수)
    - TASK_RESULT, HELP_REQUEST → `sendToOrchestrator`
    - SYSTEM → `broadcast`
  - `@ConditionalOnBean(MessagePublisher.class)`: MessagePublisher 존재 시에만 활성화
  - 단위 테스트 10개 (타입별 라우팅 5개, 검증 실패 5개)
  - provider 비활성화 시 MessageRouter 빈 미생성 검증 추가
  - [PR #43](https://github.com/jeng832/squad/pull/43) (Closes #8: 메시징 이슈 완료)

---

## 2026-02-07

### 작업 내용
- **작업 4-1: LLM 공통 인터페이스 정의**
  - 공통 모델 추가: `LlmMessage`, `LlmRequest`, `LlmResponse`, `LlmTool`, `LlmToolCall`, `LlmUsage`
  - `LlmProvider` 인터페이스 및 `LlmProviderFactory` 구현 (provider 이름으로 Optional 반환, 빈/미지원 provider는 호출처에서 처리)
  - `LlmProviderFactoryTest`로 기본 동작/미지원 provider(empty 반환) 검증

- **작업 4-2: Claude LLM Provider 구현**
  - `ClaudeProvider`: Anthropic Messages API 호출, 기본 모델/토큰/타임아웃 적용, 텍스트/ToolUse 응답을 `LlmResponse`로 매핑
  - `ClaudeConfig`: WebClient 설정 (baseUrl/apiKey 헤더, 타임아웃)
  - `ClaudeProviderTest`: WebClient exchangeFunction 스텁으로 응답 매핑 검증
  - 후속 보완: Claude 응답 텍스트 병합, temperature 전달, 미사용 ObjectMapper 제거 ([PR #32](https://github.com/jeng832/squad/pull/32))

- **작업 4-3: LLM 재시도 및 Rate Limit 처리**
  - Claude 호출 재시도 로직 추가 (429/5xx 및 네트워크/타임아웃 예외 대응)
  - 지수 백오프 + 지터 적용, 재시도 설정값 추가 (`squad.llm.claude.retry`)
  - 429 응답 후 재시도 성공 케이스 테스트 추가

- **작업 4-4: Tool Use 처리 로직**
  - `LlmToolUseService`: tool_use 감지 → tool 실행 → 후속 LLM 재호출 흐름 추가
  - `LlmToolExecutor`/`LlmToolResult` 정의 및 기본 executor 설정
  - tool_use 재호출 테스트 추가
  - llm.model Javadoc 보강
