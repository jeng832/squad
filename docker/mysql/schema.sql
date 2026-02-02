-- Squad Database Schema
-- Version: 1.0.0

USE squad;

-- =====================================================
-- Agents Table
-- AI 에이전트 정의 테이블
-- =====================================================
CREATE TABLE IF NOT EXISTS agents (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    role_type ENUM('ORCHESTRATOR', 'WORKER', 'ANALYST', 'SCRIBE', 'CUSTOM') NOT NULL,
    role TEXT NOT NULL COMMENT 'System prompt for the agent',
    llm_config JSON NOT NULL COMMENT 'LLM configuration (provider, model, apiKey reference)',
    mcps JSON DEFAULT NULL COMMENT 'List of MCP IDs assigned to this agent',
    skills JSON DEFAULT NULL COMMENT 'List of Skill IDs assigned to this agent',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_agents_name (name),
    INDEX idx_agents_role_type (role_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- MCPs Table
-- Model Context Protocol 설정 테이블
-- =====================================================
CREATE TABLE IF NOT EXISTS mcps (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    config JSON NOT NULL COMMENT 'MCP configuration (type, command, args, env)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_mcps_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- Skills Table
-- 재사용 가능한 Skill 템플릿 테이블
-- =====================================================
CREATE TABLE IF NOT EXISTS skills (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    prompt TEXT NOT NULL COMMENT 'Skill prompt template',
    required_mcps JSON DEFAULT NULL COMMENT 'List of required MCP IDs for this skill',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_skills_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- Squads Table
-- 에이전트 팀 구성 테이블
-- =====================================================
CREATE TABLE IF NOT EXISTS squads (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    orchestrator_id VARCHAR(36) NOT NULL COMMENT 'Agent ID of the orchestrator',
    direct_communication JSON DEFAULT NULL COMMENT 'Direct communication rules between agents',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_squads_name (name),
    FOREIGN KEY (orchestrator_id) REFERENCES agents(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- Squad Agents Table (Join Table)
-- Squad와 Agent의 다대다 관계 테이블
-- =====================================================
CREATE TABLE IF NOT EXISTS squad_agents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    squad_id VARCHAR(36) NOT NULL,
    agent_id VARCHAR(36) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_squad_agents_unique (squad_id, agent_id),
    FOREIGN KEY (squad_id) REFERENCES squads(id) ON DELETE CASCADE,
    FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- Sessions Table
-- 작업 실행 세션 테이블
-- =====================================================
CREATE TABLE IF NOT EXISTS sessions (
    id VARCHAR(36) PRIMARY KEY,
    squad_id VARCHAR(36) NOT NULL,
    user_prompt TEXT NOT NULL COMMENT 'Initial user prompt',
    status ENUM('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    result TEXT DEFAULT NULL COMMENT 'Final result from orchestrator',
    error_message TEXT DEFAULT NULL COMMENT 'Error message if failed',
    started_at DATETIME DEFAULT NULL,
    completed_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sessions_squad_id (squad_id),
    INDEX idx_sessions_status (status),
    INDEX idx_sessions_created_at (created_at),
    FOREIGN KEY (squad_id) REFERENCES squads(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- Messages Table
-- 에이전트 간 메시지 테이블
-- =====================================================
CREATE TABLE IF NOT EXISTS messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    from_agent_id VARCHAR(36) DEFAULT NULL COMMENT 'NULL for system messages',
    to_agent_id VARCHAR(36) DEFAULT NULL COMMENT 'NULL for broadcast messages',
    message_type ENUM('TASK_REQUEST', 'TASK_RESULT', 'HELP_REQUEST', 'HELP_RESPONSE', 'STATUS_UPDATE', 'ERROR', 'SYSTEM') NOT NULL,
    content TEXT NOT NULL,
    metadata JSON DEFAULT NULL COMMENT 'Additional metadata (tokens used, etc.)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_messages_session_id (session_id),
    INDEX idx_messages_from_agent (from_agent_id),
    INDEX idx_messages_to_agent (to_agent_id),
    INDEX idx_messages_created_at (created_at),
    FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- Secrets Table
-- 암호화된 API 키 등 민감 정보 테이블
-- =====================================================
CREATE TABLE IF NOT EXISTS secrets (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT 'Secret name for reference (e.g., claude-api-key)',
    encrypted_value TEXT NOT NULL COMMENT 'AES-256 encrypted value',
    description TEXT DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_secrets_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- Agent MCPs Table (Optional - if using join table instead of JSON)
-- 에이전트와 MCP의 다대다 관계 테이블
-- =====================================================
CREATE TABLE IF NOT EXISTS agent_mcps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id VARCHAR(36) NOT NULL,
    mcp_id VARCHAR(36) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_agent_mcps_unique (agent_id, mcp_id),
    FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE CASCADE,
    FOREIGN KEY (mcp_id) REFERENCES mcps(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- Agent Skills Table (Optional - if using join table instead of JSON)
-- 에이전트와 Skill의 다대다 관계 테이블
-- =====================================================
CREATE TABLE IF NOT EXISTS agent_skills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id VARCHAR(36) NOT NULL,
    skill_id VARCHAR(36) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_agent_skills_unique (agent_id, skill_id),
    FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE CASCADE,
    FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
