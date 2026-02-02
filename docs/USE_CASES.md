# Squad 사용자 Use Case 정의서

## 1. 개요

이 문서는 Squad 플랫폼의 주요 사용자 시나리오와 Use Case를 정의합니다.

---

## 2. Actor 정의

| Actor | 설명 |
|-------|------|
| 사용자 (User) | Squad 플랫폼을 사용하여 멀티 에이전트 협업을 구성하고 실행하는 사람 |
| 관리자 (Admin) | 시스템 설정, MCP/Skill 등록 등을 관리하는 사람 |
| Orchestrator Agent | Squad 내에서 작업을 분배하고 조율하는 에이전트 |
| Worker Agent | 실제 작업을 수행하는 에이전트 |

---

## 3. Use Case 목록

### 3.1 에이전트 관리

#### UC-001: 에이전트 생성
| 항목 | 내용 |
|------|------|
| Actor | 사용자 |
| 목적 | 새로운 AI 에이전트를 생성한다 |
| 사전조건 | 사용자가 로그인된 상태 |
| 기본흐름 | 1. 사용자가 에이전트 관리 화면에 접근<br>2. "새 에이전트 생성" 버튼 클릭<br>3. 에이전트 기본 정보 입력 (이름, roleType)<br>4. **System Prompt 입력** (에이전트의 역할과 행동 지침을 자연어로 작성)<br>5. LLM 설정 (Provider, Model 선택)<br>6. 사용할 MCP 선택<br>7. 사용할 Skill 선택<br>8. 저장 버튼 클릭<br>9. 시스템이 에이전트 생성 완료 |
| 대안흐름 | 3a. 필수 정보 미입력 시 오류 메시지 표시<br>4a. System Prompt 템플릿 제공 (roleType별 기본 템플릿) |
| 사후조건 | 에이전트가 시스템에 등록됨 |

**System Prompt 필수 입력:**
- 모든 에이전트는 반드시 System Prompt를 가져야 함
- System Prompt는 에이전트의 역할, 책임, 행동 방식을 정의
- roleType 선택 시 해당 템플릿이 자동으로 입력되며, 사용자가 수정 가능

**System Prompt 작성 가이드:**
- 에이전트의 역할과 전문 분야 정의
- 작업 수행 방식 및 제약사항 명시
- 출력 형식 가이드라인 (필요시)
- 다른 에이전트와의 협업 방식 (필요시)

---

### 3.1.1 RoleType별 System Prompt 템플릿

#### Orchestrator 템플릿
```
당신은 Squad의 Orchestrator입니다.

**역할:**
- 사용자의 요청을 분석하고 적절한 에이전트에게 작업을 분배합니다
- 각 에이전트의 결과를 취합하고 다음 단계를 결정합니다
- 에이전트가 막히면 다른 에이전트에게 도움을 요청합니다
- 모든 작업이 완료되면 최종 결과를 정리하여 반환합니다

**행동 지침:**
- 작업을 가능한 병렬로 분배하여 효율성을 높이세요
- 에이전트의 전문 분야를 고려하여 적절히 배정하세요
- 결과의 품질을 검토하고 필요시 재작업을 요청하세요
```

#### Worker 템플릿
```
당신은 Squad의 Worker입니다.

**역할:**
- Orchestrator로부터 할당받은 작업을 수행합니다
- 주어진 작업에 집중하여 최선의 결과물을 생성합니다

**행동 지침:**
- 작업 범위를 벗어나지 않도록 주의하세요
- 불명확한 부분이 있으면 Orchestrator에게 질문하세요
- 작업 완료 후 결과와 함께 수행 과정을 간략히 설명하세요

**전문 분야:**
[사용자가 구체적인 전문 분야를 입력]
```

#### Analyst 템플릿
```
당신은 Squad의 Analyst입니다.

**역할:**
- 주어진 자료, 코드, 문서를 분석합니다
- 구조, 패턴, 문제점, 개선점을 파악합니다
- 분석 결과를 명확하고 구조화된 형식으로 제공합니다

**행동 지침:**
- 객관적이고 근거 기반의 분석을 제공하세요
- 발견한 문제점에는 심각도와 우선순위를 표시하세요
- 가능하면 개선 방안도 함께 제시하세요

**분석 대상:**
[사용자가 구체적인 분석 대상/도메인을 입력]
```

#### Scribe 템플릿
```
당신은 Squad의 Scribe입니다.

**역할:**
- 작업 과정과 결과를 문서화합니다
- 다른 에이전트들의 작업 내용을 정리합니다
- 최종 보고서나 문서를 작성합니다

**행동 지침:**
- 명확하고 읽기 쉬운 문서를 작성하세요
- 중요한 결정 사항과 그 이유를 기록하세요
- 적절한 형식(마크다운, 목록 등)을 사용하세요

**문서 형식:**
[사용자가 원하는 문서 형식/스타일을 입력]
```

#### Custom 템플릿
```
당신은 Squad의 [역할명]입니다.

**역할:**
[에이전트의 주요 역할과 책임을 입력하세요]

**행동 지침:**
[에이전트가 따라야 할 행동 지침을 입력하세요]

**전문 분야/제약사항:**
[추가적인 전문 분야나 제약사항을 입력하세요]
```

---

#### UC-002: 에이전트 수정
| 항목 | 내용 |
|------|------|
| Actor | 사용자 |
| 목적 | 기존 에이전트의 설정을 수정한다 |
| 사전조건 | 수정할 에이전트가 존재함 |
| 기본흐름 | 1. 에이전트 목록에서 수정할 에이전트 선택<br>2. 수정할 정보 변경 (이름, System Prompt, LLM 설정, MCP, Skill 등)<br>3. 저장 |
| 대안흐름 | 2a. 진행 중인 세션에서 사용 중인 에이전트는 수정 불가 경고 |
| 사후조건 | 에이전트 정보가 업데이트됨 |

#### UC-003: 에이전트 삭제
| 항목 | 내용 |
|------|------|
| Actor | 사용자 |
| 목적 | 에이전트를 삭제한다 |
| 사전조건 | 삭제할 에이전트가 존재함 |
| 기본흐름 | 1. 에이전트 목록에서 삭제할 에이전트 선택<br>2. 삭제 버튼 클릭<br>3. 확인 다이얼로그에서 확인<br>4. 시스템이 에이전트 삭제 |
| 대안흐름 | 3a. Squad에 소속된 에이전트는 삭제 불가 경고 |
| 사후조건 | 에이전트가 시스템에서 제거됨 |

#### UC-004: 에이전트 목록 조회
| 항목 | 내용 |
|------|------|
| Actor | 사용자 |
| 목적 | 등록된 에이전트 목록을 조회한다 |
| 기본흐름 | 1. 에이전트 관리 화면 접근<br>2. 시스템이 에이전트 목록 표시 |
| 사후조건 | - |

---

### 3.2 MCP 관리

#### UC-005: MCP 등록
| 항목 | 내용 |
|------|------|
| Actor | 관리자 |
| 목적 | 새로운 MCP(Model Context Protocol)를 시스템에 등록한다 |
| 사전조건 | 관리자 권한 보유 |
| 기본흐름 | 1. MCP 관리 화면 접근<br>2. "MCP 등록" 버튼 클릭<br>3. MCP 정보 입력 (ID, 이름, 설명, config)<br>4. 저장 |
| 대안흐름 | 3a. config 형식 오류 시 검증 실패 메시지 |
| 사후조건 | MCP가 Registry에 등록됨 |

#### UC-006: MCP 수정
| 항목 | 내용 |
|------|------|
| Actor | 관리자 |
| 목적 | 기존 MCP 설정을 수정한다 |
| 기본흐름 | 1. MCP 목록에서 수정할 MCP 선택<br>2. 정보 수정<br>3. 저장 |
| 사후조건 | MCP 정보가 업데이트됨 |

#### UC-007: MCP 삭제
| 항목 | 내용 |
|------|------|
| Actor | 관리자 |
| 목적 | MCP를 삭제한다 |
| 대안흐름 | 에이전트가 사용 중인 MCP는 삭제 불가 |
| 사후조건 | MCP가 Registry에서 제거됨 |

---

### 3.3 Skill 관리

#### UC-008: Skill 등록
| 항목 | 내용 |
|------|------|
| Actor | 관리자/사용자 |
| 목적 | 재사용 가능한 Skill을 등록한다 |
| 기본흐름 | 1. Skill 관리 화면 접근<br>2. "Skill 등록" 버튼 클릭<br>3. Skill 정보 입력 (ID, 이름, 설명, prompt, requiredMcps)<br>4. 저장 |
| 사후조건 | Skill이 Registry에 등록됨 |

#### UC-009: Skill 수정
| 항목 | 내용 |
|------|------|
| Actor | 관리자/사용자 |
| 목적 | 기존 Skill 설정을 수정한다 |
| 사후조건 | Skill 정보가 업데이트됨 |

#### UC-010: Skill 삭제
| 항목 | 내용 |
|------|------|
| Actor | 관리자/사용자 |
| 목적 | Skill을 삭제한다 |
| 대안흐름 | 에이전트가 사용 중인 Skill은 삭제 불가 |
| 사후조건 | Skill이 Registry에서 제거됨 |

---

### 3.4 Squad 관리

#### UC-011: Squad 생성
| 항목 | 내용 |
|------|------|
| Actor | 사용자 |
| 목적 | 에이전트들의 팀(Squad)을 생성한다 |
| 사전조건 | 최소 1개의 Orchestrator 역할 에이전트 존재 |
| 기본흐름 | 1. Squad 관리 화면 접근<br>2. "새 Squad 생성" 버튼 클릭<br>3. Squad 정보 입력 (이름, 설명)<br>4. Orchestrator 에이전트 지정<br>5. 멤버 에이전트 추가<br>6. 직접 통신 규칙 설정 (선택)<br>7. 저장 |
| 대안흐름 | 4a. Orchestrator 미지정 시 오류 |
| 사후조건 | Squad가 생성됨 |

#### UC-012: Squad 수정
| 항목 | 내용 |
|------|------|
| Actor | 사용자 |
| 목적 | Squad 구성을 수정한다 |
| 기본흐름 | 1. Squad 목록에서 수정할 Squad 선택<br>2. 구성 변경 (에이전트 추가/제거, 규칙 변경)<br>3. 저장 |
| 대안흐름 | 진행 중인 세션이 있으면 수정 불가 |
| 사후조건 | Squad 구성이 업데이트됨 |

#### UC-013: Squad 삭제
| 항목 | 내용 |
|------|------|
| Actor | 사용자 |
| 목적 | Squad를 삭제한다 |
| 대안흐름 | 진행 중인 세션이 있으면 삭제 불가 |
| 사후조건 | Squad가 시스템에서 제거됨 |

---

### 3.5 세션 실행

#### UC-014: 세션 시작
| 항목 | 내용 |
|------|------|
| Actor | 사용자 |
| 목적 | Squad Template을 선택하고 프롬프트를 입력하여 작업을 시작한다 |
| 사전조건 | 사용할 Squad Template이 존재함 |
| 기본흐름 | 1. 세션 실행 화면 접근<br>2. Squad Template 선택<br>3. 프롬프트 입력<br>4. 실행 버튼 클릭<br>5. 시스템이 세션 생성 및 Active Squad 인스턴스화<br>6. Agent Container들 시작, 각 Agent가 독립적인 샌드박스(Workspace)에서 실행<br>7. Orchestrator가 작업 분배 시작 |
| 대안흐름 | 5a. LLM API 연결 실패 시 오류 표시 |
| 사후조건 | 세션이 진행 중 상태 (Active Squad 동작 중) |

**동시 세션 실행:**
- 동일한 Squad Template으로 여러 세션을 동시에 실행할 수 있음
- 각 세션은 독립적인 Active Squad를 생성하여 완전히 격리된 환경에서 동작
- 예: "기능 개발 Squad" Template으로 기능 A 개발 세션과 기능 B 개발 세션을 동시에 실행
- 각 Active Squad의 Agent들은 서로 다른 Git 브랜치에서 독립적으로 작업 가능

#### UC-015: 세션 진행 상황 모니터링
| 항목 | 내용 |
|------|------|
| Actor | 사용자 |
| 목적 | 진행 중인 세션의 상태를 실시간으로 확인한다 |
| 기본흐름 | 1. 세션 상세 화면 접근<br>2. 각 에이전트의 현재 상태 확인<br>3. 에이전트 간 메시지 흐름 확인<br>4. 실시간 업데이트 수신 |
| 사후조건 | - |

#### UC-016: 세션 결과 확인
| 항목 | 내용 |
|------|------|
| Actor | 사용자 |
| 목적 | 완료된 세션의 결과를 확인한다 |
| 사전조건 | 세션이 완료됨 |
| 기본흐름 | 1. 세션 상세 화면 접근<br>2. 최종 결과 확인<br>3. 에이전트별 작업 내역 확인<br>4. 전체 대화 히스토리 확인 |
| 사후조건 | - |

#### UC-017: 세션 중단
| 항목 | 내용 |
|------|------|
| Actor | 사용자 |
| 목적 | 진행 중인 세션을 강제 중단한다 |
| 기본흐름 | 1. 진행 중인 세션 화면에서 중단 버튼 클릭<br>2. 확인 다이얼로그<br>3. 시스템이 모든 에이전트 작업 중단<br>4. 세션 상태를 "중단됨"으로 변경 |
| 사후조건 | 세션이 중단됨 |

#### UC-018: 세션 히스토리 조회
| 항목 | 내용 |
|------|------|
| Actor | 사용자 |
| 목적 | 과거 세션 목록을 조회한다 |
| 기본흐름 | 1. 세션 히스토리 화면 접근<br>2. 세션 목록 확인 (날짜, Squad, 상태)<br>3. 필터링/검색 (선택) |
| 사후조건 | - |

---

### 3.6 모니터링 및 시각화

#### UC-019: 에이전트 실행 상태 확인
| 항목 | 내용 |
|------|------|
| Actor | 사용자 |
| 목적 | 각 에이전트의 현재 실행 상태를 확인한다 |
| 기본흐름 | 1. 모니터링 대시보드 접근<br>2. 에이전트별 상태 표시 (대기/실행중/완료)<br>3. 현재 수행 중인 작업 내용 확인 |
| 사후조건 | - |

#### UC-020: 메시지 흐름 시각화
| 항목 | 내용 |
|------|------|
| Actor | 사용자 |
| 목적 | 에이전트 간 메시지 흐름을 시각적으로 확인한다 |
| 기본흐름 | 1. 세션 시각화 화면 접근<br>2. 에이전트 간 연결 그래프 표시<br>3. 메시지 전송 시 애니메이션 표시<br>4. 특정 메시지 클릭 시 상세 내용 확인 |
| 사후조건 | - |

---

## 4. Use Case 다이어그램

```
                           +------------------+
                           |      User        |
                           +--------+---------+
                                    |
         +----------+---------------+---------------+----------+
         |          |               |               |          |
         v          v               v               v          v
   +---------+ +---------+    +---------+    +---------+ +---------+
   | Agent   | | Squad   |    | Session |    | MCP     | | Skill   |
   | 관리    | | 관리    |    | 실행    |    | 관리    | | 관리    |
   +---------+ +---------+    +---------+    +---------+ +---------+
   | 생성    | | 생성    |    | 시작    |    | 등록    | | 등록    |
   | 수정    | | 수정    |    | 모니터링|    | 수정    | | 수정    |
   | 삭제    | | 삭제    |    | 결과확인|    | 삭제    | | 삭제    |
   | 조회    | | 조회    |    | 중단    |    | 조회    | | 조회    |
   +---------+ +---------+    | 히스토리|    +---------+ +---------+
                              +---------+
```

---

## 5. 사용 시나리오

### 5.1 시나리오: 멀티 레포 코드 리뷰

**상황**: 개발자가 MSA 환경에서 여러 레포지토리에 걸친 변경사항을 리뷰하고 싶다.

**흐름**:
1. 사용자가 "코드 리뷰 Squad" 생성
   - Orchestrator: 작업 분배 및 조율
   - Analyst: 각 레포 분석
   - Worker-A: 백엔드 레포 담당
   - Worker-B: 프론트엔드 레포 담당
   - Scribe: 리뷰 결과 문서화

2. 세션 실행
   - 사용자가 Web UI에서 "코드 리뷰 Squad" 선택
   - 프롬프트 입력: "최근 PR들을 분석하고 잠재적 문제점을 찾아줘"
   - Platform Server가 세션 생성 및 Agent Container 시작

3. Orchestrator 동작 (Platform Server로부터 프롬프트 수신)
   - Analyst에게 전체 구조 분석 요청
   - Worker-A, Worker-B에게 각 레포 상세 분석 요청 (병렬)
   - 결과 취합 후 Scribe에게 문서화 요청

4. 결과 확인
   - 종합 리뷰 리포트 수신

### 5.2 시나리오: 기획서 기반 개발

**상황**: PM이 기획서를 제공하고, 이를 기반으로 코드를 생성하고 싶다.

**흐름**:
1. "개발 Squad" 구성
   - Orchestrator: 전체 조율
   - Analyst: 기획서 분석 및 요구사항 도출
   - Worker: 코드 작성
   - Scribe: 진행 상황 기록

2. 세션 실행
   - 사용자가 Web UI에서 "개발 Squad" 선택
   - 프롬프트 입력: "첨부된 기획서를 기반으로 API를 구현해줘"
   - Platform Server가 세션 생성 및 Agent Container 시작

3. 협업 진행 (Orchestrator가 작업 분배)
   - Analyst가 기획서 분석 → 요구사항 정리
   - Worker가 요구사항 기반 코드 작성
   - 막히는 부분은 Analyst에게 확인 요청
   - Scribe가 과정 문서화

4. 결과
   - 구현된 코드 + 개발 문서

### 5.3 시나리오: 점심 메뉴 추천

**상황**: 사용자가 오늘 점심으로 무엇을 먹을지 결정하기 어려워서 AI Squad에게 추천을 받고 싶다.

**Squad 구성**:
- **Orchestrator**: 전체 추천 과정 조율
- **MenuCollector** (Worker): 주변 식당 메뉴 수집
- **FoodAnalyst** (Analyst): 음식 분류 및 특성 분석
- **PreferenceAnalyst** (Analyst): 사용자 선호도 분석
- **Recommender** (Worker): 최종 추천 생성

**사용 MCP**:
- 지도/위치 API MCP (주변 식당 검색)
- 리뷰 플랫폼 API MCP (음식점 리뷰 조회)
- 사용자 히스토리 DB MCP (과거 식사 기록)

**흐름**:
1. 세션 실행
   - 사용자가 Web UI에서 "점심 추천 Squad" 선택
   - 프롬프트 입력: "오늘 점심 뭐 먹을지 추천해줘. 위치는 강남역 근처야."
   - Platform Server가 세션 생성 및 Agent Container 시작

2. Orchestrator 동작 (Platform Server로부터 프롬프트 수신)
   - MenuCollector에게 강남역 반경 500m 식당 메뉴 수집 요청
   - FoodAnalyst에게 메뉴별 음식 분류 요청 (한식/중식/일식/양식, 매운 정도, 주 재료 등)
   - PreferenceAnalyst에게 사용자 과거 식사 기록 및 리뷰 분석 요청

3. 협업 진행
   - **MenuCollector**:
     - 지도 API로 주변 식당 15개 탐색
     - 각 식당의 메뉴 및 가격 정보 수집
   - **FoodAnalyst**:
     - 수집된 메뉴 50개 분류
     - 음식 종류, 조리 방법, 주 재료, 칼로리 추정
   - **PreferenceAnalyst**:
     - 최근 2주간 사용자 식사 기록 분석 (최근에 한식을 자주 먹음)
     - 과거 리뷰에서 선호 패턴 추출 (매운 음식 선호, 면류 좋아함)
     - 최근 먹지 않은 음식 카테고리 파악 (일식은 2주간 안 먹음)

4. Recommender 동작
   - 모든 분석 결과 취합
   - 추천 알고리즘 적용:
     - 최근 안 먹은 종류 가산점
     - 선호 재료/조리법 가산점
     - 리뷰 평점 반영
   - 상위 3개 메뉴 선정 및 추천 이유 작성

5. 결과
   - **1순위**: "스시도쿄" - 런치 스시세트 (12,000원)
     - 추천 이유: 2주간 일식을 안 드셨고, 해산물을 좋아하시며, 평점 4.8
   - **2순위**: "면옥" - 매운 비빔국수 (9,000원)
     - 추천 이유: 면류 선호, 매운맛 선호, 가성비 좋음
   - **3순위**: "파스타공방" - 봉골레 파스타 (13,000원)
     - 추천 이유: 면류 선호, 양식은 1주일 전 드심, 신선한 재료 사용 리뷰 다수

### 5.4 시나리오: 여행 계획 수립

**상황**: 사용자가 3박 4일 부산 여행 계획을 세우고 싶다.

**Squad 구성**:
- **Orchestrator**: 여행 계획 조율
- **TravelResearcher** (Analyst): 관광지/맛집 정보 수집 및 분석
- **RouteOptimizer** (Worker): 동선 최적화
- **BudgetAnalyst** (Analyst): 예산 분석
- **PlanWriter** (Scribe): 여행 계획서 작성

**사용 MCP**:
- 관광 정보 API MCP
- 지도/경로 API MCP
- 숙소 예약 플랫폼 API MCP

**흐름**:
1. 세션 실행
   - 사용자가 Web UI에서 "여행 계획 Squad" 선택
   - 프롬프트 입력: "3박 4일 부산 여행 계획 짜줘. 예산은 50만원이고, 해산물과 야경을 좋아해."
   - Platform Server가 세션 생성 및 Agent Container 시작

2. 협업 진행 (Orchestrator가 작업 분배)
   - **TravelResearcher**: 부산 인기 관광지, 해산물 맛집, 야경 명소 조사
   - **RouteOptimizer**: 숙소 위치 기준 효율적인 일정 배치
   - **BudgetAnalyst**: 숙소, 식사, 교통, 입장료 예산 배분
   - **PlanWriter**: 시간대별 상세 일정표 작성

3. 결과
   - 일자별 상세 여행 계획서
   - 예상 비용 명세
   - 추천 맛집 리스트 (예약 필요 여부 포함)
   - 대안 일정 (우천 시)

### 5.5 시나리오: MSA 환경 에러 원인 파악

**상황**: 개발자가 프로덕션 로그에서 에러 메시지를 발견하고, MSA 환경에서 여러 서비스에 걸친 에러의 근본 원인을 파악하고 싶다.

**Squad 구성**:
- **Orchestrator**: 에러 분석 과정 조율, 서비스별 Worker 관리
- **Worker-OrderService** (Worker): order-service 레포지토리 담당
- **Worker-InventoryService** (Worker): inventory-service 레포지토리 담당
- **Worker-PaymentService** (Worker): payment-service 레포지토리 담당
- **SolutionAnalyst** (Analyst): 해결책 분석 및 제안
- **DocumentScribe** (Scribe): 분석 결과 문서화 및 이슈 트래킹

**사용 MCP**:
- Git Repository MCP (각 서비스 소스 코드 접근)
- Database MCP (DB 직접 조회로 데이터 검증)
- Jira MCP (이슈 생성 및 업데이트)
- Confluence/Wiki MCP (분석 결과 문서화)

**흐름**:

```
┌─────────┐     ┌─────────────────┐     ┌──────────────┐
│  User   │────▶│ Platform Server │────▶│ Orchestrator │
└─────────┘     │  (세션 생성)     │     └──────┬───────┘
                └─────────────────┘            │
                                               ▼
                        ┌─────────────┐     ┌─────────────┐
                        │  Worker-A   │────▶│  Worker-B   │
                        │ (order-svc) │     │(inventory)  │
                        └─────────────┘     └──────┬──────┘
                                                   │
                               ┌───────────────────┘
                               ▼
                        ┌──────────────┐
                        │   Analyst    │
                        └──────┬───────┘
                               │
                               ▼
                        ┌──────────────┐     ┌─────────┐     ┌──────────┐
                        │    Scribe    │────▶│  Jira   │     │   Wiki   │
                        └──────────────┘     └─────────┘     └──────────┘
```

**Step 1: 에러 발견 및 세션 시작**
- 사용자가 로그에서 에러 메시지 발견
- Web UI에서 "에러 분석 Squad" 선택 후 세션 시작
- 프롬프트 입력: "order-service에서 다음 에러가 발생했어: `InventoryCheckException: Failed to validate stock for order #12345`. 원인 분석해줘."
- Platform Server가 세션 생성 및 Agent Container들 시작
- Platform Server가 프롬프트를 Orchestrator에게 전달

**Step 2: Orchestrator 초기 분석**
- MSA 구성 파악: order-service, inventory-service, payment-service
- 에러 발생 서비스(order-service) 담당 Worker-A에게 원인 질의

**Step 3: Worker-A (order-service) 분석**
```
[Worker-A 응답]
Git MCP로 order-service 코드 분석 완료.

에러 발생 위치: OrderController.java:156
- createOrder() 메서드에서 inventory-service 호출
- inventory-service의 /api/v1/stock/validate 엔드포인트 호출 시 에러 응답 수신
- 에러 응답 코드: 500 (Internal Server Error)
- 에러 메시지: "Data integrity violation in stock table"

→ 근본 원인은 inventory-service에 있는 것으로 판단됩니다.
```

**Step 4: Orchestrator → Worker-B 전달**
- Worker-A 분석 결과를 바탕으로 inventory-service 담당 Worker-B에게 질의
- 질의 내용: "stock 테이블 데이터 정합성 관련 에러 원인 분석 요청"

**Step 5: Worker-B (inventory-service) 심층 분석**
```
[Worker-B 응답]
Git MCP로 inventory-service 코드 분석 완료.

에러 발생 위치: StockValidator.java:89
- validateStock() 메서드에서 DB 조회 후 검증 로직 수행
- 문제 발견: product_id와 warehouse_id 복합키로 조회 시
  stock_quantity가 음수(-5)인 레코드 존재

Database MCP로 검증:
> SELECT * FROM stock WHERE stock_quantity < 0;
결과: product_id='PROD-001', warehouse_id='WH-003', stock_quantity=-5

근본 원인:
- 동시성 이슈로 인한 재고 차감 중복 처리
- StockService.decreaseStock()에 트랜잭션 격리 수준 미설정
- 관련 코드: StockService.java:45-67
```

**Step 6: Orchestrator → Analyst 해결책 요청**
- 근본 원인 파악 완료
- SolutionAnalyst에게 해결책 분석 요청

**Step 7: SolutionAnalyst 해결책 제시**
```
[SolutionAnalyst 응답]

해결책 분석 결과:

1. 즉시 조치 (Hotfix)
   - 음수 재고 데이터 수동 보정: UPDATE stock SET stock_quantity = 0 WHERE stock_quantity < 0
   - 영향 범위: 3건의 레코드
   - 리스크: 낮음

2. 단기 해결책 (1-2일)
   - decreaseStock() 메서드에 비관적 락(Pessimistic Lock) 적용
   - @Lock(LockModeType.PESSIMISTIC_WRITE) 어노테이션 추가
   - 장점: 구현 간단, 동시성 문제 해결
   - 단점: 성능 저하 가능성 (대량 주문 시)

3. 중장기 해결책 (1-2주)
   - 재고 차감을 이벤트 기반으로 전환 (Event Sourcing)
   - Kafka를 통한 순차 처리로 동시성 이슈 원천 차단
   - 장점: 확장성 좋음, 재고 변경 이력 추적 가능
   - 단점: 아키텍처 변경 필요, 개발 공수 큼

권장: 1번 즉시 적용 후, 2번으로 단기 해결, 3번은 로드맵에 반영
```

**Step 8: Orchestrator → 사용자 보고**
- 전체 분석 결과와 해결책을 사용자에게 종합 보고

**Step 9: DocumentScribe 문서화**
```
[DocumentScribe 작업]

1. Jira MCP로 이슈 생성:
   - 이슈 타입: Bug
   - 제목: [P1] inventory-service 동시성 이슈로 인한 음수 재고 발생
   - 우선순위: Critical
   - 담당자: inventory-service 팀
   - 라벨: production-incident, data-integrity
   - 하위 태스크:
     - [Hotfix] 음수 재고 데이터 보정
     - [Fix] 비관적 락 적용
     - [Tech Debt] Event Sourcing 검토

2. Confluence MCP로 장애 분석 문서 작성:
   - 페이지: "2024-01-15 Order Service 장애 분석"
   - 섹션:
     - 장애 개요
     - 타임라인
     - 근본 원인 분석 (RCA)
     - 서비스 호출 흐름도
     - 해결책 및 조치 사항
     - 재발 방지 대책
```

**최종 결과물**:
- 근본 원인 분석 보고 (사용자에게 직접 전달)
- Jira 이슈 TICKET-1234 생성 완료
- Confluence 장애 분석 문서 발행 완료

### 5.6 시나리오: Slack 연동 자동 에러 분석 (고도화)

**상황**: 프로덕션 환경에서 에러 발생 시 Slack으로 알림을 받고, Slack 내에서 바로 Squad에 분석을 요청하여 결과를 쓰레드로 받는다.

**Squad 구성**:
- **Orchestrator**: 에러 분석 조율
- **LogChecker** (Worker): Grafana/로그 시스템에서 에러 로그 수집
- **Worker-{ServiceName}** (Worker): 각 MSA 서비스별 코드 분석
- **SolutionAnalyst** (Analyst): 해결책 분석
- **SlackReporter** (Scribe): Slack 쓰레드에 결과 보고

**사용 MCP**:
- Slack MCP (메시지 수신/발신, 버튼 인터랙션)
- Grafana MCP (로그 조회, 대시보드 데이터)
- Git Repository MCP (소스 코드 접근)
- Database MCP (데이터 검증)
- Jira MCP (이슈 생성)

**흐름**:

```
┌────────────────────────────────────────────────────────────────────────────┐
│                              Slack Channel                                  │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ 🚨 [ALERT] order-service 500 Error                                   │  │
│  │ Time: 2024-01-15 14:30:05 KST                                        │  │
│  │ Error: InventoryCheckException                                       │  │
│  │ Count: 127 errors in last 5 min                                      │  │
│  │                                                                      │  │
│  │ ┌─────────────────┐  ┌─────────────────┐  ┌──────────────────┐      │  │
│  │ │ 🔍 분석 요청    │  │ 📊 대시보드     │  │ 🔕 알림 끄기     │      │  │
│  │ └─────────────────┘  └─────────────────┘  └──────────────────┘      │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                                                            │
│  ↓ 사용자가 "🔍 분석 요청" 버튼 클릭                                       │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ 💬 Thread                                                            │  │
│  │ ├─ 🤖 분석을 시작합니다... (Orchestrator)                            │  │
│  │ ├─ 📋 Grafana에서 로그 수집 중... (LogChecker)                       │  │
│  │ ├─ 🔎 order-service 코드 분석 중... (Worker-Order)                   │  │
│  │ ├─ 🔎 inventory-service 코드 분석 중... (Worker-Inventory)           │  │
│  │ ├─ 💡 해결책 분석 중... (Analyst)                                    │  │
│  │ └─ ✅ 분석 완료! (최종 보고서)                                       │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────┘
```

**Step 1: Slack 알림 수신**
- Grafana/AlertManager가 에러 감지 → Slack 채널에 알림 발송
- 알림 메시지에 인터랙티브 버튼 포함:
  - `🔍 분석 요청`: Squad 분석 트리거
  - `📊 대시보드`: Grafana 대시보드 링크
  - `🔕 알림 끄기`: 일시적 알림 중지

**Step 2: 사용자 → Platform Server → Squad 트리거**
- 사용자가 "🔍 분석 요청" 버튼 클릭
- Slack이 Platform Server의 Webhook 엔드포인트로 이벤트 전송
- Platform Server가 세션 생성 및 Agent Container들 시작
- Platform Server가 알림 메시지의 컨텍스트를 Orchestrator에게 전달

**Step 3: Orchestrator 시작 알림**
```
[Slack Thread - Orchestrator]
🤖 에러 분석을 시작합니다.

분석 대상:
- 서비스: order-service
- 에러: InventoryCheckException
- 발생 시간: 2024-01-15 14:30:05 KST
- 발생 건수: 127건 (최근 5분)

진행 상황을 이 쓰레드에 업데이트하겠습니다.
```

**Step 4: LogChecker → Grafana 로그 수집**
```
[Slack Thread - LogChecker]
📋 Grafana에서 에러 로그를 수집했습니다.

수집 결과:
- 총 에러: 127건
- 첫 발생: 14:28:32
- 최다 발생 구간: 14:30:00 ~ 14:32:00 (89건)
- 에러 패턴: inventory-service 호출 시 500 응답

스택트레이스:
\`\`\`
InventoryCheckException: Failed to validate stock
  at OrderService.createOrder(OrderService.java:156)
  at InventoryClient.validateStock(InventoryClient.java:45)
  Caused by: HttpClientException: 500 Internal Server Error
\`\`\`

→ inventory-service 분석이 필요합니다.
```

**Step 5: Worker 순차 분석 (쓰레드 업데이트)**
```
[Slack Thread - Worker-Order]
🔎 order-service 분석 완료

발견:
- OrderService.java:156에서 inventory-service 호출
- 에러 원인은 inventory-service 내부에 있음

→ inventory-service 담당 Worker에게 전달합니다.
```

```
[Slack Thread - Worker-Inventory]
🔎 inventory-service 분석 완료

근본 원인 발견:
- StockValidator.java:89 - 재고 검증 로직
- stock 테이블에 음수 데이터 존재 (stock_quantity = -5)
- 원인: 동시성 미처리로 인한 재고 중복 차감

DB 검증 결과:
\`\`\`sql
SELECT * FROM stock WHERE stock_quantity < 0;
-- 결과: 3건의 음수 재고 발견
\`\`\`
```

**Step 6: Analyst 해결책 제시**
```
[Slack Thread - Analyst]
💡 해결책 분석 완료

| 구분 | 해결책 | 소요 시간 | 리스크 |
|------|--------|-----------|--------|
| 🔥 즉시 | 음수 데이터 수동 보정 | 10분 | 낮음 |
| 🔧 단기 | 비관적 락 적용 | 1-2일 | 낮음 |
| 🏗️ 중기 | Event Sourcing 전환 | 1-2주 | 중간 |

권장 순서: 즉시 → 단기 → 중기 순으로 적용
```

**Step 7: 최종 보고 및 액션 버튼**
```
[Slack Thread - SlackReporter]
✅ 에러 분석이 완료되었습니다.

📊 요약
━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• 근본 원인: inventory-service 동시성 이슈
• 영향 범위: 주문 API 127건 실패
• 심각도: 🔴 Critical

🔗 생성된 리소스
• Jira: TICKET-1234
• 문서: Confluence 장애 분석 페이지

┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ 📋 Jira 보기    │  │ 📄 문서 보기    │  │ 🔄 재분석      │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

**시퀀스 다이어그램**:
```
┌────────┐  ┌───────┐  ┌─────────┐  ┌────────────┐  ┌──────────┐  ┌─────────┐
│Grafana │  │ Slack │  │Platform │  │Orchestrator│  │ Workers  │  │ Analyst │
│        │  │       │  │ Server  │  │            │  │          │  │         │
└───┬────┘  └───┬───┘  └────┬────┘  └─────┬──────┘  └────┬─────┘  └────┬────┘
    │           │           │             │              │             │
    │──Alert───▶│           │             │              │             │
    │           │           │             │              │             │
    │           │◀─Button───│             │              │             │
    │           │   Click   │             │              │             │
    │           │──Webhook─▶│             │              │             │
    │           │           │─세션 생성──▶│              │             │
    │           │           │─Container──▶│─────────────▶│             │
    │           │           │  시작       │              │             │
    │           │           │             │──Log 조회───▶│             │
    │           │◀────────Thread Update───│              │             │
    │           │           │             │◀─로그 결과───│             │
    │           │           │             │              │             │
    │           │           │             │──코드 분석──▶│             │
    │           │◀────────Thread Update───│              │             │
    │           │           │             │◀─분석 결과───│             │
    │           │           │             │              │             │
    │           │           │             │──해결책 요청─────────────▶│
    │           │◀────────Thread Update───│              │             │
    │           │           │             │◀─해결책 제시─────────────│
    │           │           │             │              │             │
    │           │◀────────최종 보고───────│              │             │
    │           │           │             │              │             │
└───┴────┘  └───┴───┘  └────┴────┘  └─────┴──────┘  └────┴─────┘  └────┴────┘
```

**장점**:
- 컨텍스트 스위칭 없이 Slack에서 모든 분석 진행
- 실시간 진행 상황 쓰레드 업데이트
- 팀 전체가 분석 과정과 결과를 함께 확인
- 버튼을 통한 빠른 후속 조치 (Jira, 문서, 재분석)

---

## 6. 우선순위

### MVP (Phase 1)
- UC-001 ~ UC-004: 에이전트 CRUD
- UC-011 ~ UC-013: Squad CRUD
- UC-014: 세션 시작
- UC-015: 기본 모니터링
- UC-016: 결과 확인

### Phase 2
- UC-005 ~ UC-007: MCP 관리
- UC-008 ~ UC-010: Skill 관리
- UC-017: 세션 중단
- UC-018: 세션 히스토리

### Phase 3
- UC-019: 상세 모니터링
- UC-020: 메시지 흐름 시각화
- 사용자 인증/인가
