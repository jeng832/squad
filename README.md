# Squad

멀티 AI 에이전트 협업 플랫폼.

여러 AI 에이전트가 Orchestrator의 조율 하에 복잡한 작업을 수행합니다. 각 에이전트는 독립적인 Docker 컨테이너(샌드박스)에서 실행되어 완전한 격리를 보장합니다.

---

## 주요 특징

- **멀티 에이전트 협업**: Orchestrator가 Worker 에이전트들에게 작업을 분배하고 결과를 취합
- **샌드박스 격리**: 각 에이전트는 독립적인 Docker 컨테이너에서 실행, 세션 간 완전한 격리
- **Git 워크스페이스**: 에이전트별로 Git 저장소를 독립 clone하여 동시 작업 충돌 방지
- **동시 세션 실행**: 동일 Squad 템플릿으로 여러 세션을 병렬 실행 가능
- **Secret 관리**: API 키 등 민감 정보를 AES-256으로 암호화 저장, `ref:secret/<name>` 형식으로 참조
- **Built-in Tools**: 모든 에이전트에 파일 읽기/쓰기/검색, bash 실행 도구 자동 제공
- **MCP 연동**: MCP Gateway를 통해 GitHub, Slack 등 외부 서비스를 에이전트에 연결
- **인터랙티브 CLI**: 슬래시 커맨드 기반 터미널 클라이언트

---

## 기술 스택

| 항목 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Database | MySQL 8.x |
| Message Queue | Redis (Pub/Sub) |
| Container | Docker / Docker Compose |
| CLI | Picocli + JLine3 |
| LLM | Claude (Anthropic) |

---

## 아키텍처 개요

```
Client (CLI / REST)
        │
        ▼
Squad Platform Server
  ├── Agent / Squad / Session 관리
  ├── MCP Gateway (외부 서비스 도구 제공)
  └── Container 생명주기 관리
        │
        ▼
Agent Containers (Docker)
  ├── Orchestrator  ─── 작업 분배 및 결과 취합
  ├── Worker A      ─── 실제 작업 수행
  └── Worker B      ─── 실제 작업 수행
        │
    Redis (Pub/Sub) · MySQL · LLM APIs
```

에이전트 간 통신은 Redis Pub/Sub으로 처리되며, Built-in Tool은 에이전트 컨테이너 내부에서 직접 실행됩니다.

> 상세 아키텍처: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

---

## 빠른 시작

### 1. 사전 준비

- Docker / Docker Compose
- Java 21

### 2. 인프라 실행

```bash
docker compose up -d
```

MySQL(3306), Redis(6379)가 시작됩니다.

### 3. Agent 이미지 빌드

```bash
docker build -f docker/agent/Dockerfile -t squad-agent:latest .
```

### 4. 서버 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

> 전체 가이드: [docs/QUICK_START.md](docs/QUICK_START.md)

---

## CLI 사용법

```bash
# CLI 실행 (인터랙티브 모드)
./gradlew :squad-cli:run

# 또는 One-shot 모드
squad agent list
```

### 주요 슬래시 커맨드

| 커맨드 | 설명 |
|--------|------|
| `/agent` | 에이전트 생성/조회/수정/삭제 |
| `/squad` | Squad 생성/조회/수정/삭제 |
| `/session` | 세션 시작/조회/모니터링 |
| `/mcp` | MCP 서버 관리 |
| `/skill` | Skill 관리 |
| `/secret` | Secret(API 키 등) 관리 |

---

## 예시 실행

예시 Agent/Squad를 자동으로 생성하려면:

```bash
# 예시 1: 번역-요약 Squad
./scripts/setup_example1_pre_session.sh

# 예시 2: 코드 분석 Squad
./scripts/setup_example2_pre_session.sh
```

스크립트 실행 시 Claude API Key를 입력받아 Secret Store에 등록하고, 에이전트에 `ref:secret/claude-api-key` 형식으로 연결합니다.

> 예시 상세 설명: [docs/EXAMPLES.md](docs/EXAMPLES.md)

---

## API Key 설정

에이전트의 `llmConfig.apiKey`에 API 키를 설정합니다.

**Secret Store에 등록 후 참조 (권장):**

```bash
# CLI에서 Secret 등록
> /secret create
```

```json
{
  "llmConfig": {
    "provider": "claude",
    "model": "claude-sonnet-4-20250514",
    "apiKey": "ref:secret/claude-api-key"
  }
}
```

**평문 직접 입력:**

```json
{
  "llmConfig": {
    "apiKey": "sk-ant-..."
  }
}
```

> `apiKey`는 Agent 생성/수정 시 필수 항목입니다.

---

## 문서

| 문서 | 설명 |
|------|------|
| [docs/SPEC.md](docs/SPEC.md) | 프로젝트 명세 (기능 요구사항, 협업 모델) |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 시스템 아키텍처 상세 설계 |
| [docs/QUICK_START.md](docs/QUICK_START.md) | 최초 실행 가이드 및 트러블슈팅 |
| [docs/EXAMPLES.md](docs/EXAMPLES.md) | 구성 예시 (번역-요약, 코드 분석) |
| [docs/LLM_INTEGRATION.md](docs/LLM_INTEGRATION.md) | LLM Provider 연동 가이드 |

---

## DB 접속 정보 (로컬)

| 항목 | 값 |
|------|----|
| Host | localhost |
| Port | 3306 |
| Database | squad |
| Username | squad |
| Password | squad |
