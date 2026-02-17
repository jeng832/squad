# 세션 로그

## 2026-02-16

### 작업 내용
- Task 11-6: 세션 관리 CLI 커맨드 구현 (`/session`)
  - `list`: 세션 목록 테이블 출력
  - `start`: Squad 선택(화살표키) → 프롬프트 입력(멀티라인) → 확인 → 생성+시작
  - `cancel`: 세션 취소 (상태 검증 포함)
  - `status`: 세션 상세 정보 표시
  - `result`: 세션 결과 조회 (상태별 안내 메시지)
  - Codex 코드리뷰 2회 반복 반영 (NPE 방어, ID 검증, isBlank 검증, 인덱스 상한 검증)
  - SessionCommandTest 30개 테스트 케이스 작성

### PR
- [#66](https://github.com/jeng832/squad/pull/66) - 세션 관리 CLI 커맨드 구현

### 주요 결정사항
- 슬래시 커맨드 검색 bug fix: prefix 우선 필터링 적용 (커밋 0f4620f)

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

## 2026-02-10

### 작업 내용
- **7-1: 세션 시작 흐름 구현** ([PR #44](https://github.com/jeng832/squad/pull/44))
  - `SessionExecutionService` 구현: 세션 시작 lifecycle 조율
    - PENDING 상태 검증 → Container 생성/시작 → RUNNING 상태 전이 → Orchestrator에 프롬프트 전달
    - Container 시작 실패 및 DB flush 실패 시 cleanup 로직 포함
  - `SessionController`에 `POST /api/v1/sessions/{id}/start` 엔드포인트 추가
  - `SessionExecutionServiceTest` 6개 단위 테스트 작성
  - `SessionControllerTest`에 start 관련 3개 테스트 추가

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

- **7-3: Worker Agent 실행 로직 구현** ([PR #46](https://github.com/jeng832/squad/pull/46))
  - `WorkerContext`: Worker Agent별 실행 상태 관리 (CopyOnWriteArrayList, volatile Subscription)
  - `WorkerService`: 태스크 수신 → LLM 호출 → 결과 반환 전체 흐름
    - startWorker/stopWorker/stopAllWorkers lifecycle 관리
    - TASK_REQUEST 수신 시 LLM 호출 후 TASK_RESULT 반환
    - LLM 호출 실패 시 에러 결과를 Orchestrator에게 전달 (sendErrorResult)
  - `SessionExecutionService` 통합: Worker 구독 → Orchestrator 구독 → 프롬프트 발행 순서 보장
  - 단위 테스트 9개 작성 (WorkerServiceTest)
  - codex-cli 리뷰: P2 1건 (세션 완료 시 Worker 정리) → 7-4 범위로 기록

- **7-4: 세션 완료 처리 로직 구현** ([PR #47](https://github.com/jeng832/squad/pull/47))
  - `SessionCompleteHandler`: 콜백 인터페이스 도입 (순환 의존 방지)
  - `OrchestratorService.completeSession()`: 핸들러 호출로 세션 완료 위임
  - `SessionExecutionService.complete()`: 세션 COMPLETED 전이, Worker 구독 정리, Container 정리
  - `TransactionTemplate` 적용: 콜백에서의 self-invocation 시 트랜잭션 보장 (codex-cli 리뷰 반영)
  - Container 정리 시 `buildContainerName()`으로 이름 재구성
  - 단위 테스트 추가: OrchestratorServiceTest 1개, SessionExecutionServiceTest 4개
  - codex-cli 리뷰: P1 1건 (Spring AOP 프록시 우회) → TransactionTemplate으로 해결
  - **이슈 #9 완료** (Closes #9)

- **8-1: STOMP over WebSocket 설정** ([PR #48](https://github.com/jeng832/squad/pull/48))
  - `WebSocketConfig`: STOMP over WebSocket 설정 (`/ws` 엔드포인트, `/topic` Simple Broker, heartbeat 10초)
  - `SessionEvent`: 클라이언트 전송용 이벤트 DTO (sessionId, type, payload, timestamp)
  - `SessionEventType`: MESSAGE, AGENT_STATUS, SESSION_COMPLETE enum
  - `application.yml`에 `squad.websocket.allowed-origins` 설정 추가
  - 단위 테스트 4개 작성
  - codex-cli와 설계 논의: 패키지명(monitoring), CORS(명시적 origin), SockJS(불필요), Simple Broker(MVP 충분)
  - codex-cli 리뷰: P0 1건 (heartbeat TaskScheduler 누락), P2 1건 (TaskScheduler Spring 빈 관리) → 모두 해결

- **8-2: 실시간 상태 전송** ([PR #49](https://github.com/jeng832/squad/pull/49))
  - `SessionEventPublisher`: `SimpMessagingTemplate` 기반 WebSocket 이벤트 발행
    - `publishMessage()`: Agent 간 메시지 이벤트 (content 200자 truncate)
    - `publishAgentStatus()`: Agent 상태 변경 이벤트 (WORKING, IDLE)
  - `OrchestratorService` 통합: 작업 분배 시 AGENT_STATUS + MESSAGE, 결과 수신 시 AGENT_STATUS + MESSAGE
  - `WorkerService` 통합: 태스크 수신/완료 시 AGENT_STATUS 이벤트
  - 단위 테스트 5개 작성 (SessionEventPublisherTest)
  - codex-cli 리뷰 3회:
    - 1차: P2 1건 (Worker 실패 시 WORKING 상태 유지) → try-finally로 해결
    - 2차: P1 2건 (publish 실패 시 false TASK_RESULT, 세션 stuck) → publish() 예외 내부 처리로 해결
    - 3차: 이슈 없음

- **8-3: 세션 완료 알림** ([PR #50](https://github.com/jeng832/squad/pull/50))
  - `SessionEventPublisher.publishSessionComplete()`: SESSION_COMPLETE 이벤트 전송 (result 500자 truncate)
  - `SessionExecutionService.complete()`에서 트랜잭션 완료 후 이벤트 발행
  - 단위 테스트 4개 추가 (publishSessionComplete 2건, 세션 완료 이벤트 발행 검증 2건)

- **9-1: MCP 프로세스 관리자** ([PR #51](https://github.com/jeng832/squad/pull/51))
  - `McpConfig`: Mcp 엔티티 config JSON에서 command, args, env 추출
  - `McpConnection`: 실행 중인 MCP 프로세스 stdin/stdout/stderr 래핑, 안전한 종료 (destroy → destroyForcibly)
  - `McpProcessManager`: 프로세스 lifecycle 관리 (시작/종료/조회), 중복 시작 시 기존 연결 정리
  - `McpProcessException`: 프로세스 관련 예외
  - 단위 테스트 14건 (McpConfig 4건, McpConnection 3건, McpProcessManager 7건)
  - codex-cli 리뷰 7회:
    - 1차: P1 1건 (@PreDestroy 누락), P2 1건 (dead 프로세스 반환) → 해결
    - 2차: P1 1건 (start/stop 원자성), P2 1건 (config 타입 검증) → 해결
    - 3차: P1 1건 (getConnection race condition) → value-aware removal로 해결
    - 4차: P2 1건 (args null 요소 NPE) → null 검증 추가
    - 5차: P2 1건 (비IO 예외 래핑) → catch Exception으로 해결
    - 6차: P1 1건 (시작 직후 liveness 체크), P2 1건 (stopAll synchronized) → 해결
    - 7차: 이슈 없음

### 주요 결정사항
- Container 생성은 `@Transactional` 내부에서 수행하되, flush 시점을 명시적으로 관리하여 DB 실패 시 Container 정리 가능하도록 설계
- Orchestrator Container를 먼저 시작한 후 Agent Container를 순차적으로 시작
- 구독 설정 → 프롬프트 발행 순서 보장: Redis Pub/Sub 메시지 유실 방지
- 대기 작업 카운터(`AtomicInteger`) 기반 LLM 재호출 타이밍 결정

---

## 2026-02-11

### 작업 내용
- **에이전트 도구 아키텍처 설계 결정**
  - Docker 환경에서 MCP 제공 문제 분석 및 해결 방안 도출
  - **Built-in Tools vs MCP Gateway** 2계층 도구 아키텍처 확정
  - codex-cli 피드백 수렴 (동의, SPOF/지연 우려 제기, 도구 스키마 버전 관리 등 추가 제안)
  - 관련 문서 일괄 업데이트: SPEC.md, ARCHITECTURE.md, USE_CASES.md, TASKS.md

### 주요 결정사항

#### Built-in Tools (내장 도구)
- `file_read`, `file_write`, `file_search`, `bash_exec` 등 필수 도구는 Agent Runtime에 직접 구현
- 모든 에이전트에 자동 제공, 사용자가 선택하지 않아도 포함
- MCP 프로토콜을 거치지 않고 컨테이너 내부에서 직접 실행
- Squad가 보안/권한 직접 제어 (예: `/workspace` 밖 접근 차단)

#### MCP Gateway (외부 서비스 도구)
- MCP 서버는 에이전트 컨테이너 밖에서 MCP Gateway가 중앙 관리
- 에이전트는 SSE/HTTP로 MCP Gateway에 접근
- 에이전트 컨테이너에 MCP 설치 불필요
- 기존 `McpProcessManager`(9-1)는 MCP Gateway 내부에서 활용

#### 설계 배경
- MCP 조합별 Docker 이미지 빌드 시 경우의 수 폭발
- MCP 설치 방법이 비표준 (npx, uvx, 바이너리 등)
- 파일/셸 같은 필수 도구를 MCP에 의존하는 것은 과도함

#### codex-cli 피드백 요약
- 구조에 대체로 동의
- 우려: MCP Gateway SPOF, 네트워크 홉 지연, 멀티테넌시 격리 복잡성
- 추가 제안: 도구 스키마 버전 관리, 동기/비동기 분리, 장애 내성, 권한 모델 설계

### 문서 변경 내역
- **SPEC.md**: 용어 정의에 `Built-in Tool`, `MCP Gateway` 추가, 에이전트 설정 JSON에서 `file` MCP 제거, 신규 섹션 4.7 "에이전트 도구 아키텍처" 추가
- **ARCHITECTURE.md**: High-Level 아키텍처에 MCP Gateway 추가, Agent Container 구조에서 `MCP Client` → `Built-in Tools` + `MCP GW Client`로 변경, 도구 실행 흐름을 Built-in/Gateway 분기로 변경
- **USE_CASES.md**: UC-001 에이전트 생성에 Built-in Tools 자동 제공 안내 추가, MCP 선택을 외부 서비스로 명확화
- **TASKS.md**: 9-2~9-5 설명을 Gateway 방식으로 업데이트, 9-6 (MCP Gateway 서비스), 9-7 (Built-in Tools 구현) 신규 추가, Phase 1 총계 45→47개

---

### 작업 9-2: MCP JSON-RPC 클라이언트 구현
- **PR**: [#52](https://github.com/jeng832/squad/pull/52)
- **구현 내용**:
  - JSON-RPC 2.0 메시지 모델 (record): `JsonRpcRequest`, `JsonRpcResponse`, `JsonRpcNotification`, `JsonRpcError`
  - `McpClient`: `initialize()`, `listTools()`, `callTool()` — 동기식 단일 in-flight, synchronized
  - `McpClientException`, `McpToolInfo`, `McpToolCallResult` 도메인 모델
  - 24개 단위 테스트
- **설계 결정**:
  - Phase 1: 동기식 + `synchronized`, Phase 2: 비동기 리더 + CompletableFuture
  - `volatile boolean initialized` — thread-safety
  - `timeoutMillis` 양수 검증 필수
  - 서버 요청 시 원본 id 타입 보존 (ObjectNode으로 직접 구성)
  - initialize 재시도는 상위 오케스트레이션 책임 (SRP)
- **codex-cli 코드리뷰**: 3회 반복 리뷰 후 합의 완료
  - 1차: synchronized, volatile, timeout 검증, JSON 파싱 강화
  - 2차: id 타입 보존, result null/NullNode 검증
  - 3차: 숫자 id 보존 테스트 추가 → 최종 합의

### 작업 9-3: MCP Tool Registration (도구 등록 레지스트리)
- **PR**: [#53](https://github.com/jeng832/squad/pull/53)
- **구현 내용**:
  - `ToolRoute` record: alias(`mcpName__toolName`) ↔ (mcpName, originalToolName) 라우팅 매핑
    - `of()` 팩토리 메서드: null/blank 검증 포함
    - `aliasOf()` 정적 메서드: alias 문자열만 생성 (ToolRoute 객체 생성 없이)
  - `McpToolRegistry`: MCP 서버 도구 등록/조회/해제 중앙 레지스트리
    - `registerMcp(McpConfig)`: 프로세스 시작 → initialize → tools/list → 캐시 저장, 실패 시 rollback
    - `getTools(String)`, `getAllTools()`, `getToolsForMcps(List<String>)`: LlmTool 형식 조회
    - `findRoute(String alias)`: alias → ToolRoute 라우팅 조회 (9-4에서 활용)
    - `getClient(String)`: McpClient 반환 (9-4에서 활용)
    - `unregisterMcp(String)`, `unregisterAll()`: 등록 해제 + 프로세스 종료
    - McpToolInfo → LlmTool 변환 (JsonNode inputSchema → Map<String, Object>)
  - 단위 테스트 17개 (ToolRouteTest 4개, McpToolRegistryTest 13개)
    - MockedConstruction<McpClient>로 내부 생성 객체 모킹
- **설계 결정**:
  - 단일 `synchronized` 락 기반 동시성 모델 (여러 Map 복합 연산 원자성 보장)
  - HashMap 사용 (ConcurrentHashMap 불필요 — 모든 접근이 synchronized)
  - 실패 시 `processManager.stop()` rollback + `addSuppressed` 예외 체이닝
  - 재등록 시 `clearMcpState()`로 기존 캐시 무효화 후 재등록
  - alias 충돌(`__` 포함 이름) 문제는 후속 Phase에서 대응 (현재 Phase 1 MVP)
- **codex-cli 코드리뷰**: 3회 반복 리뷰 후 합의 완료
  - 1차: 리소스 누수, rollback 부재, 혼합 동시성 모델 등 8건 → 전면 수정
  - 2차: 재등록 프로세스 정지, 예외 마스킹, raw type 등 6건 → 수정/보류 합의
  - 3차: 이슈 없음 → 승인

---

## 2026-02-15

### 작업 내용

- **TASKS.md 구조 개선**: Phase 1에 CLI 인터페이스 추가, Phase 2에 React Web UI 확장
  - Phase 1: 11. CLI 인터페이스 (11-1 ~ 11-7, 7개 작업) 추가
  - Phase 2: 모니터링 대시보드 → React 관리 대시보드로 확장 (14-1 ~ 14-8)
  - 전체 번호 재정렬, 총계 91개 → 101개

### 작업 9-4: MCP Tool 실행 통합
- **PR**: [#54](https://github.com/jeng832/squad/pull/54)
- **구현 내용**:
  - `McpToolExecutor`: `LlmToolExecutor` 구현체 (MCP Gateway 경유 도구 실행)
    - alias → ToolRoute 라우팅 → McpClient 호출 → 결과 변환
    - 에러 시 예외 대신 에러 텍스트 반환 (LLM 재시도 가능)
  - `LlmToolUseService`: 단일 호출 → while 루프 개선
    - 연쇄 tool_use 지원 (최대 10회 반복)
    - 메시지 누적 방식으로 대화 히스토리 유지
  - `WorkerService`: MCP 도구 통합
    - `LlmToolUseService` + `McpToolRegistry` 주입
    - MCP 도구 목록을 LLM 요청에 포함
    - 미사용 `LlmProviderFactory` 직접 의존 제거
  - 단위 테스트 21개 (McpToolExecutorTest 8개, LlmToolUseServiceTest 5개, WorkerServiceTest 8개)
- **설계 결정**:
  - `McpToolExecutor`는 `mcp.gateway` 패키지 배치 (포트/어댑터 경계 분리)
  - 도구 실패는 예외 throw 대신 에러 텍스트 반환 (LLM이 오류 인식 후 재시도 가능)
  - Worker의 MCP 도구 범위: `getAllTools()` (Agent별 필터링은 후속 작업)
  - OrchestratorService는 수정하지 않음 (내부 도구만 사용)
- **codex-cli 코드리뷰**: 1회 리뷰, 이슈 없음 → 승인

### 작업 9-5: MCP 환경변수 및 Secret 주입
- **PR**: [#55](https://github.com/jeng832/squad/pull/55)
- **구현 내용**:
  - `EnvResolver`: 환경변수에서 `ref:secret/<name>` 참조를 `SecretService`로 복호화하여 치환
  - `McpConfig.withResolvedEnv()`: 해결된 환경변수로 새 불변 객체 생성
  - `McpToolRegistry.registerMcp()`: 프로세스 시작 전 Secret 참조 해결 통합
  - Fail-Closed 정책: Secret 해결 실패 시 프로세스 시작 차단
- **설계 결정**:
  - 방안 B (별도 EnvResolver 컴포넌트) 채택: SRP 준수, 값 객체가 서비스에 의존하지 않음
  - McpConfig는 raw env 보관, 해결된 env는 withResolvedEnv()로 새 불변 객체 생성
- **codex-cli 코드리뷰**: P1 이슈 1건 (Secret 해결 실패 시 기존 프로세스 orphan 방지) → 수정 완료

### 작업 9-6: MCP Gateway 서비스
- **PR**: [#56](https://github.com/jeng832/squad/pull/56)
- **구현 내용**:
  - `McpGatewayService` 추가: MCP 등록/해제, Tool 목록 조회, Tool 호출, SSE 이벤트 스트림 제공
  - `McpGatewayController` 추가: Agent Runtime용 HTTP/SSE 엔드포인트(`/api/v1/mcp-gateway/**`)
  - DTO 추가: `McpToolCallRequest`, `McpGatewayEvent`
  - 단위 테스트 추가: `McpGatewayServiceTest`
- **설계 결정**:
  - 기존 `McpToolRegistry`, `McpToolExecutor`를 재사용해 Gateway 계층만 추가
  - 이벤트 스트림은 Reactor `Sinks.Many` 기반 multicast로 구현
  - Tool 호출은 alias 기반(`mcpName__toolName`) 라우팅 유지

### 작업 9-7: Built-in Tools 구현
- **PR**: [#57](https://github.com/jeng832/squad/pull/57)
- **구현 내용**:
  - `BuiltInToolRegistry` 추가: `file_read`, `file_write`, `file_search`, `bash_exec` 도구 정의(JSON Schema) 제공
  - `BuiltInToolExecutor` 추가: `/workspace` 기준 경로 검증 후 내장 도구 실행
    - `file_read`: 파일 읽기
    - `file_write`: 파일 쓰기/append
    - `file_search`: glob + optional pattern 기반 파일 검색
    - `bash_exec`: allowlist 기반 제한 명령 실행(메타문자 차단, workspace 경계 검증, 타임아웃/출력 길이 제한)
  - `CompositeToolExecutor` 추가: Built-in 우선, 그 외는 `McpToolExecutor`로 라우팅
  - `WorkerService` 수정: LLM 요청 도구 목록에 Built-in + MCP 도구를 함께 전달
  - `bash_exec` 도구 메타데이터 개선: 허용 명령 allowlist를 tool description/command schema에 명시하고 `find` 대신 `file_search` 사용 가이드 추가
  - 단위 테스트 추가/보강:
    - `BuiltInToolExecutorTest` (파일 I/O, 검색, 경로 이탈 차단, 제한 명령 실행 검증)
    - `WorkerServiceTest` (Built-in 도구 레지스트리 주입 반영)
- **설계 결정**:
  - 도구 실행 인터페이스(`LlmToolExecutor`)는 유지하고 `@Primary` 합성 실행기로 라우팅
  - 경로 보안은 `squad.builtin-tools.workspace-root`(기본 `/workspace`) 기준 정규화 + startsWith 검증
  - 오류는 예외 throw 대신 `[오류]` 텍스트로 반환해 LLM tool_use 루프와 일관성 유지
- **Claude + codex-cli 교차 코드리뷰** (4회 반복, 양쪽 이슈 없을 때까지):
  - 1차 (Claude 리뷰): P1 3건, P2 6건, P3 3건 → 전체 수정
    - P1: bash_exec symlink 탈출 방지 (`validatePlainFilenameSymlink`)
    - P1: exitCode != 0 시 throw 제거 (exitCode + output 반환)
    - P1: file_search에서 `Files.walk()` 시 symlink 탐색 방지
    - P2: bash_exec pipe deadlock 방지 (`drainOutputWithTimeout`)
    - P2: file_read 10MB, file_write 5MB, file_search depth 20 제한
    - P3: javadoc, Comparator 개선, @DisplayName 추가
  - 2차 (codex-cli 리뷰): P1 1건 (output drain OOM), P2 1건 (기본 glob `**/*` 매치 누락) → 수정
  - 3차 (합의 논의): CompositeToolExecutor null 방어 추가
  - 4차 (codex-cli 리뷰): file_write 크기 제한을 바이트 기반으로 변경
  - 최종: 양쪽 모두 추가 리뷰 건 없음 → 종료

### 작업 10-1, 10-2, 10-3: 테스트 커버리지 100% 달성
- **PR**: [#58](https://github.com/jeng832/squad/pull/58)
- **구현 내용**:
  - **10-1: Service 단위 테스트 7개** (AgentServiceTest, McpServiceTest, SkillServiceTest, SquadServiceTest, SecretServiceTest, SessionServiceTest, MessageServiceTest)
    - `@ExtendWith(MockitoExtension.class)` + `@Mock` Repository + `@InjectMocks` Service 패턴
    - 정상 CRUD 동작, NotFoundException, ValidationException 검증
    - McpService config 검증, SquadService orchestrator role 검증, SecretService ref 형식 검증
  - **10-2: Controller 통합 테스트 2개** (McpGatewayControllerTest, AgentContainerHealthControllerTest)
    - `@WebMvcTest` + `@MockitoBean` + MockMvc 패턴 (기존 AgentControllerTest와 동일)
    - MCP 등록/해제/도구 조회/호출, Health Check 검증
  - **10-3: 세션 실행 E2E 테스트 1개** (SessionExecutionE2ETest)
    - `@SpringBootTest` + Testcontainers Redis 기반 Spring 통합 테스트
    - Docker/LLM은 `@MockitoBean`으로 대체
    - 전체 흐름(생성→시작→완료), Container 실패 시 정리, 취소 흐름, 상태 검증
  - 전체 테스트 수: 437 → 443 (신규 테스트 메서드 ~93개)
  - 모든 테스트 통과 확인 (0 failures, 0 ignored)

### 작업 11-1: CLI 프로젝트 셋업
- **PR**: [#60](https://github.com/jeng832/squad/pull/60)
- **이슈**: [#59](https://github.com/jeng832/squad/issues/59) (CLI 인터페이스) 생성
- **구현 내용**:
  - `:squad-cli` Gradle 서브모듈 생성 (Picocli 4.7.7 + JLine3 3.25.1 + Spring Boot 3.4.2)
  - Spring Boot DI 컨테이너만 사용 (`web-application-type: none`)
  - `SquadCliApplication`: REPL/One-shot 분기 진입점
  - `SquadCliCommand`: Picocli 최상위 커맨드
  - `SpringPicocliFactory`: Spring DI ↔ Picocli IFactory 브릿지
  - `RestClientConfig` + `SquadApiClient`: RestClient + Virtual Threads 기반 API 클라이언트
  - `InteractiveShell`: JLine3 REPL 루프 (프롬프트, 자동완성, 히스토리)
  - `CommandRegistry`: 슬래시 커맨드 등록/조회 (등록 순서 유지, 대소문자 무시)
  - `SlashCommandPalette`: 퍼지 검색 기반 커맨드 팔레트
  - `FuzzySearchEngine`: 레벤슈타인 거리 + 부분매칭 + 연속매칭 가점
  - `TableRenderer`: 테이블 포맷 출력 (한글 와이드 문자 너비 지원)
  - 단위 테스트 3개: CommandRegistryTest, FuzzySearchEngineTest, TableRendererTest
  - 17개 파일 생성/수정, 전체 빌드 성공 (기존 모듈 영향 없음)

## 2026-02-16

### 작업 11-2: Agent 관리 CLI 커맨드
- **PR**: [#62](https://github.com/jeng832/squad/pull/62)
- **구현 내용**:
  - `CommandContext` 레코드 추가: LineReader, Terminal, PrintWriter를 묶어 커맨드에 전달
  - `InteractiveFormReader`: 재사용 가능한 대화형 폼 유틸리티
    - `readLine()`: 한 줄 입력 (기본값 지원)
    - `readMultiLine()`: 여러 줄 입력 (@파일경로로 파일 읽기 지원), 빈 입력과 취소 구분
    - `readSelection()`: 번호 선택
    - `readConfirm()`: y/n 확인
  - `AgentCommand`: `/agent` 서브커맨드 처리
    - `list`: 에이전트 목록 테이블 출력
    - `create`: 가이드 폼 (이름 → roleType 선택 → 시스템 프롬프트 → LLM 설정 → 확인)
    - `update {id}`: 기존 값 표시, Enter로 유지, 빈 프롬프트=유지 vs Ctrl+C=취소 구분
    - `delete {id}`: 에이전트명 표시 후 확인
    - `{id}`: 상세 조회 (apiKey 마스킹)
  - `CommandExecutor` 인터페이스: `(CommandContext ctx, String args)` 시그니처로 변경
  - 18개 테스트 케이스 (list/detail/delete/masking 시나리오)
- **코드리뷰 (Codex CLI, 4회 반복 → 이슈 없을 때까지)**:
  - 1차: [High] readMultiLine의 cancel vs "keep value" 모호성 → 빈 문자열과 null 구분으로 해결
  - 1차: [Medium] 상세 조회 NPE 방어 → extractField() 활용으로 null-safe 처리
  - 1차: [Medium] apiKey 마스킹 테스트 누락 → 3개 테스트 추가 (ref:secret, 일반값, 짧은값)
  - 2차: [P1] API Key 평문 입력 화면 노출 및 히스토리 저장 위험 → readSecret() 마스킹 입력 메서드 추가
  - 3차: [P2] readFromFile에서 trim()이 파일 공백 제거 → trim 제거, [P2] update NPE → extractField 적용
  - 4차: 추가 이슈 없음 → 승인 종료
- **설계 결정**:
  - CommandContext를 record로 구현 (불변, 간결)
  - InteractiveFormReader를 별도 컴포넌트로 분리 (MCP, Squad 등 다른 커맨드에서 재사용)
  - 생성자에서 CommandRegistry에 등록하는 패턴 (Spring DI + 자동 등록)

### readSelection 화살표키 선택 UI 개선
- `InteractiveFormReader.readSelection()`: 번호 입력 → 화살표키(↑↓) 선택 UI로 변경
  - `terminal.enterRawMode()` + `NonBlockingReader.read(timeout)` 사용
  - ESC 시퀀스 감지로 화살표키 vs ESC 단독 구분 (50ms 타임아웃)
  - ANSI 이스케이프 시퀀스로 화면 갱신
- `AgentCommand`: update/delete에서 ID 미지정 시 에이전트 목록 화살표키 선택 지원

### 작업 11-3: MCP 관리 CLI 커맨드
- **PR**: [#63](https://github.com/jeng832/squad/pull/63)
- **구현 내용**:
  - `McpCommand`: `/mcp` 서브커맨드 처리
    - `list`: MCP 목록 테이블 출력 (ID, 이름, 설명, 커맨드)
    - `create`: 가이드 폼 (이름 → 설명 → config JSON 입력 → 확인)
    - `update`: ID 미지정 시 화살표키 선택, 기존값 기본값 지원, config 유지 가능
    - `delete`: ID 미지정 시 화살표키 선택, 확인 후 삭제
    - `{id}`: 상세 조회 (config JSON pretty print)
  - config JSON 입력: `readMultiLine()` + `@파일경로` 지원
    - JSON 객체 타입 검증, `command` 키 필수 검증
    - 파싱 실패 시 에러 출력 후 재입력 유도 (최대 3회)
  - `McpCommandTest`: 21개 테스트 케이스
- **코드리뷰 (Codex CLI, 4회 반복)**:
  - 1차: [P2] update에서 description 취소 미처리 → null 체크 추가
  - 2차: [P2] create/update의 optional field null 처리 → `readOptionalLine` 헬퍼 도입
  - 3차~4차: 동일 P2 반복 (InteractiveFormReader.readLine API 구조적 한계) → 수용 합의
- **설계 결정**:
  - AgentCommand 패턴 완전 답습 (등록, 서브커맨드, 선택 UI)
  - config JSON은 전체 교체 방식 (부분 병합 대비 버그 적고 테스트 용이)
  - Optional field 처리: readLine의 null 반환을 "입력 없음"으로 취급 (구조적 한계 수용)

### 작업 11-4: Squad 관리 CLI 커맨드
- **PR**: [#64](https://github.com/jeng832/squad/pull/64)
- **구현 내용**:
  - `SquadCommand`: `/squad` 서브커맨드 처리
    - `list`: Squad 목록 테이블 출력 (ID, 이름, 설명, Orchestrator ID, 멤버 수)
    - `create`: 가이드 폼 (이름 → 설명 → Orchestrator 선택 → 멤버 ID 입력 → 직접 통신 JSON → 확인)
    - `update`: ID 미지정 시 화살표키 선택, 기존값 기본값 지원, 멤버/직접통신 변경 여부 선택
    - `delete`: ID 미지정 시 화살표키 선택, 확인 후 삭제
    - `{id}`: 상세 조회 (직접 통신 JSON pretty print)
  - Orchestrator 선택: Agent 목록에서 ORCHESTRATOR roleType만 필터링
  - 멤버 Agent: 쉼표 구분 ID 입력 (사용 가능 Agent 목록 표시, 잘못된 ID 경고 후 무시, 중복 제거)
  - 직접 통신 규칙: JSON 입력 (예시 제공, 최대 3회 재시도, 빈 입력 시 건너뜀)
  - 22개 테스트 케이스
- **Codex CLI 코드리뷰 결과** (4회 반복):
  - 1차: P1 - update 시 directCommunication 미변경 시에도 기존값 null로 덮어씀 → 기존값 보존으로 수정
  - 2차: 이슈 없음
  - 3차: P1 - readAgentIds에서 Ctrl+C와 빈 입력 미구분 → null 반환으로 취소 처리 분리
  - 4차: 이슈 없음 (수렴)
- **설계 결정**:
  - 멤버 선택: 멀티 선택 UI 미구현으로 쉼표 구분 ID 입력 방식 채택 (향후 개선 가능)
  - update 시 PUT body에 항상 기존값 포함하여 서버 side null 덮어쓰기 방지
  - readConfirm 화살표키 선택 UI 적용 (readSelection 재사용)

### readMultiSelection 멀티 선택 UI 구현
- `InteractiveFormReader.readMultiSelection()`: 화살표키(↑↓) + 스페이스바 토글 + Enter 확정
  - `[x]`/`[ ]` 마커로 선택 상태 표시
  - `preSelected` 파라미터로 기존 선택값 복원 지원
- `SquadCommand.readAgentIds()`: 쉼표 구분 입력 → 멀티 선택 UI로 교체

### 에디터 기반 JSON/텍스트 입력 지원
- `InteractiveFormReader.readWithEditor()`: 외부 에디터(vi/nano/etc) 기반 텍스트 편집
  - `$VISUAL` → `$EDITOR` → `vi` 순서로 에디터 결정
  - 임시 파일 + `terminal.pause()`/`resume()` 패턴
- `InteractiveFormReader.readJsonInput()`: 에디터/직접입력/건너뛰기 3가지 선택
- JSON 파싱 실패 시 재시도 루프 ("다시 입력하시겠습니까?" 확인)
- McpCommand, SquadCommand에 에디터 기반 JSON 입력 적용

### 작업 11-5: Skill/Secret 관리 CLI
- **PR**: [#65](https://github.com/jeng832/squad/pull/65)
- **구현 내용**:
  - `SkillCommand`: `/skill` 서브커맨드 처리
    - `list`: Skill 목록 테이블 출력 (ID, 이름, 설명, 필요 MCP 수)
    - `create`: 가이드 폼 (이름 → 설명 → 프롬프트(에디터/직접입력) → requiredMcps(멀티선택) → 확인)
    - `update`: 기존값 기본값 지원, 프롬프트/MCP 변경 여부 선택
    - `delete`: ID 미지정 시 화살표키 선택, 확인 후 삭제
    - `{id}`: 상세 조회 (프롬프트 전문, requiredMcps 표시)
  - `SecretCommand`: `/secret` 서브커맨드 처리
    - `list`: Secret 목록 테이블 출력 (ID, 이름, 참조 형식, 생성일)
    - `create`: 이름 → 값(readSecret 마스킹) → 확인
    - `update`: 이름 "수정 불가" 표시, 값만 수정
    - `delete`: 확인 후 삭제
    - `{id}`: 상세 조회 (값 `********` 마스킹, `ref:secret/{name}` 참조)
  - `InteractiveFormReader.resolveEditorName()` public 메서드 추가
  - SkillCommandTest 16개, SecretCommandTest 16개 테스트
- **Codex CLI 코드리뷰 (4회 반복)**:
  - 1차: P1 - MCP API 실패 시 `List.of()` 반환으로 기존 MCP 덮어쓰기 → null 반환으로 수정
  - 1차: P2 - handleCreate에서 requiredMcps null 미처리 → null 체크 추가
  - 2차: P2 - MCP 서버 장애 시 Skill 생성 불가 → create에서 null → 빈 리스트 대체
  - 3차: P2 - MCP 멀티선택 ESC 취소 시 동작 불일치 → 컨텍스트별 분리
  - 4차: P2 - update에서 API 실패 시 기존 MCP 삭제 → null 시 기존값 유지
- **설계 결정**:
  - MCP null 처리: create(null→빈리스트), update(null→기존값유지)
  - Secret 이름 수정 불가: `ref:secret/{name}` 참조 깨짐 방지
  - Skill 프롬프트 입력: 에디터/직접입력 2가지 선택 (건너뛰기 없음, 프롬프트 필수)
