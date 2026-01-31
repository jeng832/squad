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