# 세션 로그

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
