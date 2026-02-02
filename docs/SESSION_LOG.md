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

### 다음 할 일
- Phase 1 MVP 구현 시작
  - Spring Boot 프로젝트 기본 구조 생성
  - Agent CRUD API 구현
  - Squad CRUD API 구현
  - Claude LLM Provider 구현
  - 기본 세션 실행 로직 구현

---

## 2026-02-02

### 작업 내용
- **이슈 #3: 프로젝트 초기 설정 완료**

- **1-1. Spring Boot 프로젝트 초기화**
  - Gradle 설정 (Java 21, Spring Boot 3.3.0)
  - 의존성: Spring Web, JPA, Validation, Redis, WebSocket, Docker-java, Lombok 등
  - `build.gradle`, `settings.gradle` 생성

- **1-2. 패키지 구조 및 공통 모듈 생성**
  - 패키지 구조: domain, repository, service, controller, config, common
  - 공통 예외 처리: GlobalExceptionHandler, BusinessException, ErrorCode
  - API 응답 형식: ApiResponse, ErrorResponse
  - 설정 클래스: JpaConfig, JacksonConfig, RedisConfig, WebSocketConfig
  - 공통 엔티티: BaseEntity
  - 열거형: RoleType, SessionStatus, MessageType

- **1-3. Docker Compose 인프라 구성**
  - MySQL 8.0 컨테이너 (squad-mysql)
  - Redis 7 Alpine 컨테이너 (squad-redis)
  - squad-network 브릿지 네트워크
  - 헬스체크 및 볼륨 설정

- **1-4. 데이터베이스 스키마 생성**
  - DDL 스크립트 (schema.sql)
  - 테이블: agents, mcps, skills, squads, squad_agents, sessions, messages, secrets
  - 조인 테이블: agent_mcps, agent_skills

### 생성된 파일 (25개)
- `build.gradle`, `settings.gradle`, `gradlew`, `gradle/wrapper/gradle-wrapper.properties`
- `docker-compose.yml`, `docker/mysql/init.sql`, `docker/mysql/schema.sql`
- `src/main/java/com/squad/SquadApplication.java`
- `src/main/java/com/squad/common/exception/*` (3개)
- `src/main/java/com/squad/common/response/*` (2개)
- `src/main/java/com/squad/config/*` (4개)
- `src/main/java/com/squad/domain/*` (4개)
- `src/main/resources/application.yml`
- `src/test/java/com/squad/SquadApplicationTests.java`
- `.env.example`

### 다음 할 일
- 이슈 #4: 엔티티 및 Repository 계층 구현
  - Agent, MCP, Skill, Squad, Session, Message, Secret 엔티티
  - Repository 인터페이스
  - 기본 테스트