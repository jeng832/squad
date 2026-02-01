# Squad Specification

## 1. 개요

### 1.1 프로젝트 목적

멀티 AI 에이전트를 정의하고 관리하여, 복잡한 작업을 협업으로 수행하게 하는 플랫폼

### 1.2 문제 정의

- MSA 환경에서 각 레포지토리가 분리되어 있음
- 기존 코딩 도구(Cursor, Claude Code 등)는 단일 레포 단위로 동작
- 여러 레포를 종합한 분석/판단이 불가능

### 1.3 해결 방안

- 여러 에이전트가 각각의 역할을 수행
- 에이전트 간 협업을 통해 종합적 결과 도출
- 사용자가 에이전트 구성을 자유롭게 정의

### 1.4 확장성

- 코딩 외 다른 도메인에도 적용 가능 (투자, 리서치 등)
- 초기에는 코딩에 초점

---

## 2. 용어 정의

| 용어 | 설명 |
|------|------|
| Agent | 사용자가 정의한 AI 에이전트. 이름, 역할, LLM 설정 등을 가짐 |
| RoleType | 에이전트의 역할 유형 (orchestrator, worker, analyst, scribe, custom) |
| Squad | 에이전트들의 팀 구성. 협업 단위 |
| Session | 한 번의 작업 실행 단위. 사용자 프롬프트로 시작 |
| Message | 에이전트 간 주고받는 메시지 |
| Conversation | 세션 내 메시지들의 흐름 |
| MCP | Model Context Protocol. 외부 도구 연동 규격 |
| MCP Registry | 시스템에서 사용 가능한 MCP 목록 관리 |
| Skill | 특정 작업을 수행하는 재사용 가능한 기능 단위 |
| Skills Registry | 시스템에서 사용 가능한 Skill 목록 관리 |

---

## 3. Role Type 정의

시스템에서 정의하는 에이전트 역할 유형. 사용자는 roleType을 지정하여 에이전트를 생성한다.

| roleType | 설명 | 필수 여부 |
|----------|------|----------|
| orchestrator | 작업 분배 및 조율. 전체 흐름 관리. Squad의 중심 | Squad당 1개 필수 |
| worker | 실제 작업 수행 (코드 작성, 분석 등) | 선택 |
| analyst | 결과 분석 및 종합 | 선택 |
| scribe | 과정 기록 및 문서화 | 선택 |
| custom | 사용자 정의 역할 | 선택 |

---

## 4. 기능 요구사항

### 4.1 에이전트 관리

- 에이전트 생성/수정/삭제
- Web UI를 통한 관리
- 에이전트 설정 시 등록된 MCP, Skill 중 선택 가능

**에이전트 설정 (JSON):**

```json
{
  "id": "analyst-001",
  "name": "Analyst",
  "roleType": "analyst",
  "role": "기획서와 코드를 분석하여 요구사항을 도출합니다.",
  "llm": {
    "provider": "claude",
    "model": "claude-sonnet-4-20250514",
    "apiKey": "ref:secret/claude-api-key"
  },
  "mcps": ["github", "file"],
  "skills": ["analyze-code", "summarize"]
}
```

### 4.2 MCP 관리

- MCP 등록/수정/삭제 (MCP Registry)
- Web UI를 통한 관리
- 에이전트 설정 시 등록된 MCP 목록에서 선택

**MCP 설정 (JSON):**

```json
{
  "id": "github",
  "name": "GitHub MCP",
  "description": "GitHub 저장소 연동",
  "config": {
    "type": "stdio",
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-github"],
    "env": {
      "GITHUB_TOKEN": "ref:secret/github-token"
    }
  }
}
```

### 4.3 Skills 관리

- Skill 등록/수정/삭제 (Skills Registry)
- Web UI를 통한 관리
- 에이전트 설정 시 등록된 Skill 목록에서 선택

**Skill 설정 (JSON):**

```json
{
  "id": "analyze-code",
  "name": "코드 분석",
  "description": "코드를 분석하여 구조와 문제점을 파악",
  "prompt": "다음 코드를 분석하여 구조, 의존성, 잠재적 문제점을 정리해주세요.",
  "requiredMcps": ["file"]
}
```

### 4.4 Squad 관리

- Squad 생성/수정/삭제
- 에이전트들을 팀으로 구성
- Orchestrator 에이전트 지정 (필수)
- 직접 통신 규칙 설정 (선택)

**Squad 설정 (JSON):**

```json
{
  "id": "code-review-squad",
  "name": "코드 리뷰 팀",
  "description": "여러 레포의 코드를 분석하고 리뷰",
  "orchestrator": "orchestrator-001",
  "agents": ["analyst-001", "worker-001", "worker-002", "scribe-001"],
  "directCommunication": {
    "enabled": false,
    "rules": [
      { "from": "worker-001", "to": "worker-002", "allowed": true }
    ]
  }
}
```

**Validation 규칙:**
- orchestrator로 지정된 에이전트는 roleType이 "orchestrator"여야 함
- Squad에는 최소 1개의 orchestrator가 필수

### 4.5 세션 실행

- Squad 선택 + 사용자 프롬프트로 세션 시작
- Orchestrator가 동적으로 다른 에이전트에게 작업 분배
- 에이전트 간 메시지 전달
- Orchestrator가 최종 완료 판단 후 결과 반환

### 4.6 모니터링/시각화

- 에이전트 실행 상태 실시간 확인
- 실행 과정 시각화 (어떤 에이전트가 어떤 작업 중인지)
- 에이전트 간 메시지 흐름 시각화
- 세션 히스토리 조회

---

## 5. 협업 모델

### 5.1 기본 방식: Orchestrator 중심

모든 통신은 기본적으로 Orchestrator를 거친다.

```
       [사용자]
          │
          ▼
   [Platform Server]  ← 세션 생성, 컨테이너 관리
          │
          ▼ (Redis)
    [Orchestrator]
     ↙    ↓    ↘
[Agent] [Agent] [Agent]
```

**동작 방식:**
1. 사용자가 Web UI/API를 통해 Platform Server에 세션 시작 요청
2. Platform Server가 세션 생성 및 Agent Container들을 시작
3. Platform Server가 사용자 프롬프트를 Redis를 통해 Orchestrator에게 전달
4. Orchestrator가 판단하여 적절한 에이전트에게 작업 요청
5. 에이전트가 작업 수행 후 결과를 Orchestrator에게 반환
6. Orchestrator가 다음 액션 결정 (다른 에이전트 호출, 추가 분석 요청 등)
7. 필요시 반복
8. Orchestrator가 완료 판단 후 최종 결과를 Platform Server에 반환
9. Platform Server가 사용자에게 결과 전달

**Orchestrator의 역할:**
- 어떤 에이전트에게 어떤 일을 시킬지 판단
- 에이전트 응답 보고 다음 액션 결정
- 에이전트가 막히면 다른 에이전트에게 도움 요청
- 완료 조건 판단

### 5.2 확장 옵션: 직접 통신 허용

필요한 경우 특정 에이전트 간 직접 통신을 허용할 수 있다.

```
              [Orchestrator]
             ↙    ↓    ↘
       [Agent] [Agent] [Agent]
          ↑_______↓
         (허용된 경우 직접 통신)
```

**설정:**
```json
{
  "directCommunication": {
    "enabled": true,
    "rules": [
      { "from": "worker-001", "to": "analyst-001", "allowed": true }
    ]
  }
}
```

---

## 6. 비기능 요구사항

### 6.1 기술 스택

| 항목 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Build | Gradle |
| Database | MySQL |
| Container | Docker |
| Config Format | JSON |

### 6.2 실행 환경

- 로컬 Docker 환경에서 실행
- 각 에이전트는 Docker 컨테이너로 실행
- 에이전트 간 통신: HTTP (Docker Network)

### 6.3 보안

- API Key 암호화 저장
- Secret 참조 형식: `ref:secret/<secret-name>`
- Secret Store: MySQL (secrets 테이블, AES256 암호화)
- 복호화 키는 환경변수로 관리

### 6.4 확장성

- 새로운 LLM Provider 추가 용이
- 새로운 MCP 추가 용이
- 새로운 Skill 추가 용이

---

## 7. 핵심 흐름

### 7.1 세션 실행 흐름

```
User (Web UI / API Client)
  │
  ▼
[Squad 선택 + 프롬프트 입력]
  │
  ▼
[Platform Server: Session 생성]
  │
  ├─→ Agent Container들 시작 (Docker)
  │
  ▼
[Platform Server → Redis → Orchestrator 실행]
  │
  ├─→ "Analyst, 이 기획서 분석해줘"
  │         │
  │         ▼
  │   [Analyst 실행] ──→ 결과 반환
  │         │
  │←────────┘
  │
  ├─→ "Worker A, 이 작업 해줘"
  ├─→ "Worker B, 이 작업 해줘" (병렬)
  │         │
  │         ▼
  │   [Workers 실행] ──→ 결과 반환
  │         │
  │←────────┘
  │
  ├─→ (Worker A가 막힘)
  │    "Analyst, 이 부분 확인해줘"
  │         │
  │         ▼
  │   [Analyst 실행] ──→ 확인 결과
  │         │
  │    "Worker A, 여기 확인 결과야"
  │         │
  │         ▼
  │   [Worker A 계속] ──→ 완료
  │         │
  │←────────┘
  │
  ├─→ "Scribe, 이 과정 정리해줘"
  │         │
  │         ▼
  │   [Scribe 실행] ──→ 문서화 완료
  │
  ▼
[Orchestrator: 완료 판단]
  │
  ▼
[Session 완료 + 최종 결과 반환]
```

### 7.2 에이전트 실행 상세

```
[Agent 실행 요청 (from Orchestrator)]
  │
  ▼
[Agent Config 로드]
  │
  ├─→ LLM 설정 로드
  ├─→ MCP 연결
  └─→ Skills 로드
  │
  ▼
[프롬프트 구성]
  │
  ├─→ System Prompt (role)
  ├─→ Skill Prompts (해당되는 경우)
  └─→ Input Message (Orchestrator로부터)
  │
  ▼
[LLM 호출]
  │
  ▼
[MCP 도구 사용 (필요시)]
  │
  ▼
[결과 반환 → Orchestrator]
```

---

## 8. 향후 고려사항

- 사용자 인증/인가
- 멀티 테넌트 지원
- 에이전트 템플릿 마켓플레이스
- Squad 템플릿 공유
- 실행 비용 추적 (토큰 사용량)
- 에이전트 성능 분석
