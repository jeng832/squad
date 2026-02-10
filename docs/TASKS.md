# Squad 프로젝트 작업 목록

이 문서는 전체 개발 로드맵의 작업 목록입니다. 각 작업은 1개의 PR로 코드리뷰가 가능한 크기로 분리되어 있습니다.

---

## Phase 1: Core (MVP)

### 1. 프로젝트 초기 설정 ([#3](https://github.com/jeng832/squad/issues/3))

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 1-1 | Spring Boot 프로젝트 초기화 | Gradle 설정, Java 21, 의존성 추가 (Spring Web, JPA, Validation, Redis, Docker 등) | build.gradle, application.yml | [#14](https://github.com/jeng832/squad/pull/14) |
| 1-2 | 패키지 구조 및 공통 모듈 생성<br/> | 패키지 구조 생성, 공통 예외 처리(GlobalExceptionHandler), API 응답 형식(ApiResponse), 공통 설정 클래스 | 10~15개 파일 | [#15](https://github.com/jeng832/squad/pull/15) |
| 1-3 | Docker Compose 인프라 구성 | MySQL, Redis 컨테이너 설정, 네트워크 구성 | docker-compose.yml, 초기화 스크립트 | [#16](https://github.com/jeng832/squad/pull/16) |
| 1-4 | 데이터베이스 스키마 생성 | ERD 기반 DDL 스크립트 작성 (agents, squads, sessions, messages, mcps, skills, secrets 테이블) | schema.sql | [#17](https://github.com/jeng832/squad/pull/17) |

---

### 2. 엔티티 및 Repository 계층 ([#4](https://github.com/jeng832/squad/issues/4))

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 2-1 | Agent 엔티티 및 Repository | Agent JPA 엔티티, AgentRepository, 기본 테스트 | 3~4개 파일 | [#18](https://github.com/jeng832/squad/pull/18) |
| 2-2 | MCP 엔티티 및 Repository | Mcp JPA 엔티티, McpRepository, 기본 테스트 | 3~4개 파일 | [#19](https://github.com/jeng832/squad/pull/19) |
| 2-3 | Skill 엔티티 및 Repository | Skill JPA 엔티티, SkillRepository, 기본 테스트 | 3~4개 파일 | [#20](https://github.com/jeng832/squad/pull/20) |
| 2-4 | Squad 엔티티 및 Repository | Squad 엔티티, SquadAgent (다대다 조인 테이블), SquadRepository | 4~5개 파일 | [#21](https://github.com/jeng832/squad/pull/21) |
| 2-5 | Session/Message 엔티티 및 Repository | Session, Message JPA 엔티티, Repository | 4~5개 파일 | [#22](https://github.com/jeng832/squad/pull/22) |
| 2-6 | Secret 엔티티 및 암호화 서비스 | Secret 엔티티, AES256 암호화/복호화 유틸, SecretRepository | 4~5개 파일 | [#23](https://github.com/jeng832/squad/pull/23) |

---

### 3. CRUD API 구현 ([#5](https://github.com/jeng832/squad/issues/5))

| # | 작업명 | 설명 | 예상 변경 범위 | PR                                              |
|---|--------|------|---------------|-------------------------------------------------|
| 3-1 | Agent CRUD API | AgentService, AgentController, DTO (Request/Response), 유효성 검증 | 5~6개 파일 | [#24](https://github.com/jeng832/squad/pull/24) |
| 3-2 | MCP CRUD API | McpService, McpController, DTO, config JSON 검증 로직 | 5~6개 파일 | [#25](https://github.com/jeng832/squad/pull/25) |
| 3-3 | Skill CRUD API | SkillService, SkillController, DTO | 5~6개 파일 | [#26](https://github.com/jeng832/squad/pull/26) |
| 3-4 | Squad CRUD API | SquadService (Orchestrator 필수 검증 포함), SquadController, DTO | 6~7개 파일 | [#27](https://github.com/jeng832/squad/pull/27) |
| 3-5 | Secret CRUD API | SecretService (암호화 저장), SecretController, 참조 해결 로직 (`ref:secret/...`) | 5~6개 파일 | [#28](https://github.com/jeng832/squad/pull/28) |
| 3-6 | Session 기본 API | SessionService (생성/조회/취소), SessionController, DTO, 상태 관리 (PENDING, RUNNING, COMPLETED, CANCELLED) | 6~7개 파일 | [#29](https://github.com/jeng832/squad/pull/29) |
| 3-7 | Message 조회 API | MessageService, MessageController, 세션별 메시지 조회 | 4~5개 파일 | [#30](https://github.com/jeng832/squad/pull/30) |

---

### 4. LLM 연동 ([#6](https://github.com/jeng832/squad/issues/6))

| # | 작업명 | 설명 | 예상 변경 범위 | PR      |
|---|--------|------|---------------|---------|
| 4-1 | LLM 공통 인터페이스 정의 | LlmProvider 인터페이스, LlmRequest/LlmResponse DTO, ToolCall 모델, ProviderFactory | 5~6개 파일 | [#31](https://github.com/jeng832/squad/pull/31) |
| 4-2 | Claude LLM Provider 구현 | ClaudeProvider 구현, HTTP 클라이언트 (WebClient), 요청/응답 매핑, 에러 처리 | 4~5개 파일 | [#32](https://github.com/jeng832/squad/pull/32) |
| 4-3 | LLM 재시도 및 Rate Limit 처리 | Exponential Backoff 재시도 로직, 429/5xx 에러 처리 | 2~3개 파일 | [#33](https://github.com/jeng832/squad/pull/33) |
| 4-4 | Tool Use 처리 로직 | LLM 응답에서 tool_use 감지, 툴 실행 결과를 다시 LLM에 전달하는 흐름 | 3~4개 파일 | [#34](https://github.com/jeng832/squad/pull/34) |

---

### 5. Agent Container ([#7](https://github.com/jeng832/squad/issues/7))

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 5-1 | Agent Container Dockerfile | squad-agent 이미지용 Dockerfile, 기본 Agent Runner 진입점 | Dockerfile, 2~3개 파일 | [#35](https://github.com/jeng832/squad/pull/35) |
| 5-2 | Agent Runner 기본 구조 | Agent 컨테이너 내부에서 실행될 Runner 애플리케이션, 환경변수 파싱, 설정 로드 | 4~5개 파일 | [#36](https://github.com/jeng832/squad/pull/36) |
| 5-3 | Docker Client 연동 | Docker API 클라이언트 (docker-java), 컨테이너 생성/시작/중지/삭제 | 3~4개 파일 | [#37](https://github.com/jeng832/squad/pull/37) |
| 5-4 | Container Lifecycle Manager | 세션 시작 시 컨테이너 생성, 세션 종료 시 정리, 컨테이너 명명 규칙 (`squad-{sessionId}-{agentId}`) | 3~4개 파일 | [#38](https://github.com/jeng832/squad/pull/38) |
| 5-5 | Container Health Check | Health 엔드포인트, 주기적 상태 확인, 비정상 컨테이너 재시작 | 3~4개 파일 | [#39](https://github.com/jeng832/squad/pull/39) |

---

### 6. 메시징 (Redis Pub/Sub) ([#8](https://github.com/jeng832/squad/issues/8))

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 6-1 | Redis 연결 설정 | RedisTemplate 설정, 연결 풀 설정 | 2~3개 파일 | [#40](https://github.com/jeng832/squad/pull/40) |
| 6-2 | Message Publisher 구현 | 세션/에이전트 채널로 메시지 발행 (`session:{id}:agent:{agentId}`) | 2~3개 파일 | [#41](https://github.com/jeng832/squad/pull/41) |
| 6-3 | Message Subscriber 구현 | 채널 구독, 메시지 수신 리스너, Agent Runner에서 사용. MessageSubscriber 인터페이스 + RedisMessageSubscriber 구현체 패턴 적용, Subscription 반환으로 lifecycle 관리, messageSerializer 빈과 Subscriber 역직렬화 경로 정리 | 6개 파일 | [#42](https://github.com/jeng832/squad/pull/42) |
| 6-4 | 메시지 라우팅 로직 | Orchestrator↔Agent 간 메시지 라우팅, 메시지 타입별 처리 (TASK_REQUEST, TASK_RESULT 등) | 4개 파일 | [#43](https://github.com/jeng832/squad/pull/43) |

---

### 7. 세션 실행 핵심 로직 ([#9](https://github.com/jeng832/squad/issues/9))

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 7-1 | 세션 시작 흐름 구현 | 세션 생성 → Squad 로드 → Container 시작 → Orchestrator에 프롬프트 전달 | 4~5개 파일 | [#44](https://github.com/jeng832/squad/pull/44) |
| 7-2 | Orchestrator 작업 분배 로직 | Orchestrator의 LLM 호출, 작업 분배 결정, Agent에게 태스크 전달 | 4~5개 파일 | [#45](https://github.com/jeng832/squad/pull/45) |
| 7-3 | Worker Agent 실행 로직 | 태스크 수신 → LLM 호출 → 결과 반환 | 3~4개 파일 | [#46](https://github.com/jeng832/squad/pull/46) |
| 7-4 | 세션 완료 처리 | Orchestrator의 완료 판단, 최종 결과 취합, 세션 상태 업데이트, Container 정리. **Worker 구독 정리 포함 (7-3 codex-cli 리뷰). [보류 검토] Subscription에 AutoCloseable 추가 여부 판단 (구독 누수 발생 시 적용, 6-3 codex-cli 합의)** | 3~4개 파일 | |

---

### 8. 실시간 모니터링 (WebSocket) ([#10](https://github.com/jeng832/squad/issues/10))

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 8-1 | WebSocket 설정 | STOMP over WebSocket 설정, 엔드포인트 (`/ws/sessions/{sessionId}`) | 2~3개 파일 | |
| 8-2 | 실시간 상태 전송 | Agent 상태 변경 시 WebSocket으로 클라이언트에 푸시 (AGENT_STATUS, MESSAGE 이벤트) | 3~4개 파일 | |
| 8-3 | 세션 완료 알림 | SESSION_COMPLETE 이벤트 전송, 연결 정리 | 2~3개 파일 | |

---

### 9. MCP 연동 ([#11](https://github.com/jeng832/squad/issues/11))

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 9-1 | MCP 프로세스 관리자 | MCP 서버 프로세스 시작/종료, stdin/stdout 통신 설정 | 3~4개 파일 | |
| 9-2 | MCP 클라이언트 구현 | JSON-RPC 요청/응답 처리, tools/list, tools/call 메서드 구현 | 4~5개 파일 | |
| 9-3 | MCP Tool 등록 | Agent 시작 시 MCP에서 사용 가능한 Tool 목록 조회, LLM에 Tool 정의 전달 | 3~4개 파일 | |
| 9-4 | MCP Tool 실행 통합 | LLM tool_use 응답 → MCP Tool 실행 → 결과를 LLM에 반환하는 전체 흐름 | 3~4개 파일 | |
| 9-5 | MCP 환경변수 및 Secret 주입 | MCP config의 환경변수 처리, `ref:secret/...` 참조 해결 후 프로세스에 주입 | 2~3개 파일 | |

---

### 10. 테스트 ([#12](https://github.com/jeng832/squad/issues/12))

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 10-1 | 단위 테스트 작성 | Service 계층 단위 테스트, Mock을 활용한 의존성 분리 | 테스트 파일 다수 | |
| 10-2 | API 통합 테스트 | MockMvc를 활용한 REST API 테스트, 시나리오별 테스트 | 테스트 파일 다수 | |
| 10-3 | 세션 실행 E2E 테스트 | Testcontainers로 Docker 환경 구성, 전체 플로우 테스트 | 2~3개 테스트 파일 | |

---

## Phase 1 요약

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
| MCP 연동 | 5개 |
| 테스트 | 3개 |
| **Phase 1 총계** | **45개** |

---

## Phase 2: Enhancement

### 11. OpenAI Provider 추가

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 11-1 | OpenAI Provider 구현 | OpenAiProvider 클래스, Chat Completions API 연동 | 3~4개 파일 | |
| 11-2 | OpenAI Function Calling 처리 | Function Calling 요청/응답 매핑, Tool 정의 변환 | 2~3개 파일 | |
| 11-3 | OpenAI 스트리밍 지원 | SSE 기반 스트리밍 응답 처리 (선택적) | 2~3개 파일 | |

---

### 12. Skill 관리 고도화

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 12-1 | Skill 템플릿 엔진 | 프롬프트 템플릿 변수 치환 (`{{variable}}` 형식) | 2~3개 파일 | |
| 12-2 | Agent-Skill 매핑 | Agent에 Skill 할당, 복수 Skill 지원 | 3~4개 파일 | |
| 12-3 | Skill 기반 프롬프트 빌더 | Agent의 role + skills를 조합하여 system prompt 생성 | 2~3개 파일 | |
| 12-4 | Skill 버전 관리 | Skill 수정 이력 관리, 버전별 롤백 지원 | 3~4개 파일 | |

---

### 13. 모니터링 대시보드

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 13-1 | 세션 목록 조회 UI | React 기반 세션 목록 페이지, 상태별 필터링 | 3~4개 파일 | |
| 13-2 | 세션 상세 뷰 | 세션 정보, 참여 Agent 목록, 실시간 상태 표시 | 3~4개 파일 | |
| 13-3 | 메시지 타임라인 | Agent 간 메시지 흐름을 시간순으로 표시 | 3~4개 파일 | |
| 13-4 | Agent 상태 모니터링 | 각 Agent의 현재 상태, 처리 중인 작업 표시 | 2~3개 파일 | |
| 13-5 | 에러 로그 뷰어 | 세션/Agent별 에러 로그 조회 및 검색 | 3~4개 파일 | |

---

### 14. Container 고급 관리

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 14-1 | Container 리소스 제한 | CPU, Memory 제한 설정 (Docker resource limits) | 2~3개 파일 | |
| 14-2 | Container 로그 수집 | Agent Container 로그 수집 및 저장 | 3~4개 파일 | |
| 14-3 | Container 재시작 정책 | 실패 시 자동 재시작, 최대 재시도 횟수 설정 | 2~3개 파일 | |
| 14-4 | Container 메트릭 수집 | CPU/Memory 사용량 모니터링, Prometheus 연동 (선택적) | 3~4개 파일 | |

---

### 15. API 고도화

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 15-1 | 페이지네이션 | 목록 API에 페이지네이션 적용 (offset/limit, cursor 방식) | 3~4개 파일 | |
| 15-2 | 필터링 및 정렬 | 상태별, 날짜별 필터, 정렬 옵션 | 2~3개 파일 | |
| 15-3 | API 버전 관리 | /api/v1, /api/v2 버전 분리 구조 | 2~3개 파일 | |
| 15-4 | API 문서 자동화 | SpringDoc OpenAPI (Swagger) 연동 | 2~3개 파일 | |

---

## Phase 2 요약

| 카테고리 | 작업 수 |
|----------|---------|
| OpenAI Provider | 3개 |
| Skill 관리 고도화 | 4개 |
| 모니터링 대시보드 | 5개 |
| Container 고급 관리 | 4개 |
| API 고도화 | 4개 |
| **Phase 2 총계** | **20개** |

---

## Phase 3: Advanced

### 16. Gemini Provider 추가

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 16-1 | Gemini Provider 구현 | GeminiProvider 클래스, Gemini API 연동 | 3~4개 파일 | |
| 16-2 | Gemini Function Calling 처리 | Gemini 스타일 Function 정의 및 호출 처리 | 2~3개 파일 | |
| 16-3 | 멀티모달 지원 | 이미지 입력 처리 (Gemini Vision) | 3~4개 파일 | |

---

### 17. Agent 직접 통신

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 17-1 | 직접 통신 규칙 설정 | Squad 설정에서 Agent 간 직접 통신 허용 규칙 정의 | 2~3개 파일 | |
| 17-2 | 직접 통신 라우터 | Orchestrator 거치지 않는 Agent 간 메시지 라우팅 | 3~4개 파일 | |
| 17-3 | 통신 권한 검증 | 직접 통신 규칙 기반 메시지 허용/거부 처리 | 2~3개 파일 | |
| 17-4 | 직접 통신 로깅 | 직접 통신 메시지도 Message 테이블에 기록 | 2~3개 파일 | |

---

### 18. 메시지 흐름 시각화

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 18-1 | 시퀀스 다이어그램 생성 | 세션의 메시지 흐름을 시퀀스 다이어그램으로 변환 | 3~4개 파일 | |
| 18-2 | 인터랙티브 플로우 뷰어 | 노드/엣지 기반 Agent 통신 그래프 (React Flow 등) | 4~5개 파일 | |
| 18-3 | 실시간 플로우 업데이트 | WebSocket으로 진행 중인 세션 플로우 실시간 반영 | 2~3개 파일 | |
| 18-4 | 플로우 내보내기 | PNG, SVG, JSON 형식으로 플로우 다이어그램 내보내기 | 2~3개 파일 | |

---

### 19. 세션 히스토리 분석

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 19-1 | 세션 통계 API | 세션별 소요 시간, 메시지 수, 토큰 사용량 통계 | 3~4개 파일 | |
| 19-2 | Agent 성능 분석 | Agent별 평균 응답 시간, 성공률, 작업 처리량 | 3~4개 파일 | |
| 19-3 | LLM 비용 추적 | Provider별 API 호출 횟수, 토큰 사용량, 예상 비용 | 3~4개 파일 | |
| 19-4 | 분석 대시보드 | 통계 시각화 대시보드 (차트, 그래프) | 4~5개 파일 | |
| 19-5 | 리포트 생성 | 일별/주별/월별 사용량 리포트 생성 및 내보내기 | 3~4개 파일 | |

---

### 20. Container 오토스케일링

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 20-1 | 부하 모니터링 | 세션 큐 길이, Container 리소스 사용량 모니터링 | 3~4개 파일 | |
| 20-2 | 스케일링 정책 정의 | CPU/Memory 임계값 기반 스케일 아웃/인 규칙 | 2~3개 파일 | |
| 20-3 | Container Pool 관리 | 미리 생성된 Container Pool로 시작 시간 단축 | 3~4개 파일 | |
| 20-4 | Kubernetes 연동 (선택적) | K8s Deployment/HPA 연동으로 클러스터 레벨 스케일링 | 4~5개 파일 | |

---

### 21. 고급 보안

| # | 작업명 | 설명 | 예상 변경 범위 | PR |
|---|--------|------|---------------|-----|
| 21-1 | API 인증 | JWT 기반 API 인증 | 3~4개 파일 | |
| 21-2 | 사용자 권한 관리 | Role 기반 접근 제어 (Admin, User) | 3~4개 파일 | |
| 21-3 | API Rate Limiting | 사용자/IP별 API 호출 제한 | 2~3개 파일 | |
| 21-4 | 감사 로그 | API 호출, 세션 생성 등 주요 액션 로깅 | 3~4개 파일 | |

---

## Phase 3 요약

| 카테고리 | 작업 수 |
|----------|---------|
| Gemini Provider | 3개 |
| Agent 직접 통신 | 4개 |
| 메시지 흐름 시각화 | 4개 |
| 세션 히스토리 분석 | 5개 |
| Container 오토스케일링 | 4개 |
| 고급 보안 | 4개 |
| **Phase 3 총계** | **24개** |

---

## 전체 요약

| Phase | 작업 수 | 목표 |
|-------|---------|------|
| Phase 1: Core (MVP) | 45개 | 핵심 기능 동작 |
| Phase 2: Enhancement | 20개 | 기능 확장 및 UX 개선 |
| Phase 3: Advanced | 24개 | 고급 기능 및 엔터프라이즈 대응 |
| **총계** | **89개** | |

---

## 작업 순서 권장

### Phase 1
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
9-1 → 9-2 → 9-3 → 9-4 → 9-5
        ↓
10-1 → 10-2 → 10-3
```

### Phase 2
```
11-1 → 11-2 → 11-3
        ↓
12-1 → 12-2 → 12-3 → 12-4
        ↓
13-1 → 13-2 → 13-3 → 13-4 → 13-5
        ↓
14-1 → 14-2 → 14-3 → 14-4
        ↓
15-1 → 15-2 → 15-3 → 15-4
```

### Phase 3
```
16-1 → 16-2 → 16-3
        ↓
17-1 → 17-2 → 17-3 → 17-4
        ↓
18-1 → 18-2 → 18-3 → 18-4
        ↓
19-1 → 19-2 → 19-3 → 19-4 → 19-5
        ↓
20-1 → 20-2 → 20-3 → 20-4
        ↓
21-1 → 21-2 → 21-3 → 21-4
```

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
