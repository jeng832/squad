-- ============================================================
-- Squad 데이터베이스 스키마 (ERD 기반 DDL)
-- MySQL 8.0 / utf8mb4
-- ------------------------------------------------------------
-- 참조 무결성 정책: 명시적 FK 제약조건 미사용
-- ERD의 관계는 논리적 관계이며, 참조 무결성은
-- 애플리케이션 트랜잭션 내 로직으로 처리
-- ============================================================

-- ------------------------------------------------------------
-- 기본 테이블
-- ------------------------------------------------------------

-- agents: AI 에이전트 정의
CREATE TABLE IF NOT EXISTS agents (
    id         BIGINT                                                          AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100)                                                    NOT NULL,
    role_type  ENUM('orchestrator', 'worker', 'analyst', 'scribe', 'custom') NOT NULL,
    role       TEXT                                                            NOT NULL,
    llm_config JSON                                                            NOT NULL,
    created_at DATETIME(3)                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)                                                     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'AI 에이전트 정의';

-- mcps: Model Context Protocol 설정 저장소
CREATE TABLE IF NOT EXISTS mcps (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    config     JSON         NOT NULL,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'MCP 설정 저장소';

-- skills: 재사용 가능한 프롬프트 템플릿
-- required_mcps: 해당 Skill 실행에 필요한 MCP ID 목록 (JSON 배열)
CREATE TABLE IF NOT EXISTS skills (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL UNIQUE,
    description   TEXT,
    prompt        TEXT         NOT NULL,
    required_mcps JSON,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Skill (재사용 가능한 프롬프트 템플릿)';

-- secrets: AES-256 암호화된 민감 정보 저장소
-- value 컬럼에는 암호화된 값이 저장되며, 복호화 키는 환경변수(SECRET_ENCRYPTION_KEY)로 관리
CREATE TABLE IF NOT EXISTS secrets (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    encrypted_value TEXT         NOT NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '암호화된 Secret 저장소 (AES-256)';

-- ------------------------------------------------------------
-- squads 및 조인 테이블
-- ------------------------------------------------------------

-- squads: 에이전트 팀 구성 (Orchestrator 필수)
-- direct_communication: 직접 통신 규칙 JSON {"enabled": bool, "rules": [...]}
CREATE TABLE IF NOT EXISTS squads (
    id                   BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name                 VARCHAR(100) NOT NULL,
    description          TEXT,
    orchestrator_id      BIGINT       NOT NULL,
    direct_communication JSON,
    created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Squad (에이전트 팀 구성)';

-- squad_agents: Squad ↔ Agent 다대다 조인 테이블
CREATE TABLE IF NOT EXISTS squad_agents (
    squad_id BIGINT NOT NULL,
    agent_id BIGINT NOT NULL,
    PRIMARY KEY (squad_id, agent_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Squad-Agent 다대다 조인 테이블';

-- agent_mcps: Agent ↔ MCP 다대다 조인 테이블
CREATE TABLE IF NOT EXISTS agent_mcps (
    agent_id BIGINT NOT NULL,
    mcp_id   BIGINT NOT NULL,
    PRIMARY KEY (agent_id, mcp_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent-MCP 다대다 조인 테이블';

-- agent_skills: Agent ↔ Skill 다대다 조인 테이블
CREATE TABLE IF NOT EXISTS agent_skills (
    agent_id BIGINT NOT NULL,
    skill_id BIGINT NOT NULL,
    PRIMARY KEY (agent_id, skill_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent-Skill 다대다 조인 테이블';

-- ------------------------------------------------------------
-- 세션 및 메시지
-- ------------------------------------------------------------

-- sessions: 작업 실행 단위
-- status: PENDING(생성) → RUNNING(실행 중) → COMPLETED(완료) / CANCELLED(취소)
CREATE TABLE IF NOT EXISTS sessions (
    id           BIGINT                                                 AUTO_INCREMENT PRIMARY KEY,
    squad_id     BIGINT                                                 NOT NULL,
    user_prompt  TEXT                                                   NOT NULL,
    status       ENUM('PENDING', 'RUNNING', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    result       TEXT,
    started_at   DATETIME(3),
    completed_at DATETIME(3),
    created_at   DATETIME(3)                                            NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_sessions_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '세션 (작업 실행 단위)';

-- messages: 에이전트 간 메시지 기록
-- from_agent_id / to_agent_id: 시스템 메시지의 경우 NULL 가능
-- type: TASK_REQUEST, TASK_RESULT, HELP_REQUEST, HELP_RESPONSE 등
CREATE TABLE IF NOT EXISTS messages (
    id            BIGINT      AUTO_INCREMENT PRIMARY KEY,
    session_id    BIGINT      NOT NULL,
    from_agent_id BIGINT,
    to_agent_id   BIGINT,
    content       TEXT        NOT NULL,
    type          VARCHAR(50) NOT NULL,
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_messages_session_created (session_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '에이전트 간 메시지';
