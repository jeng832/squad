package com.squad.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 애플리케이션 전역 에러 코드를 정의하는 열거형.
 *
 * <p>각 에러 코드는 HTTP 상태 코드와 기본 메시지를 포함합니다.</p>
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // === Common ===
    INTERNAL_SERVER_ERROR(500, "내부 서버 오류가 발생했습니다."),
    INVALID_REQUEST(400, "잘못된 요청입니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),
    FORBIDDEN(403, "접근 권한이 없습니다."),
    METHOD_NOT_ALLOWED(405, "지원하지 않는 HTTP 메서드입니다."),

    // === Resource Not Found ===
    RESOURCE_NOT_FOUND(404, "요청한 리소스를 찾을 수 없습니다."),
    AGENT_NOT_FOUND(404, "에이전트를 찾을 수 없습니다."),
    SQUAD_NOT_FOUND(404, "Squad를 찾을 수 없습니다."),
    SESSION_NOT_FOUND(404, "세션을 찾을 수 없습니다."),
    MCP_NOT_FOUND(404, "MCP를 찾을 수 없습니다."),
    SKILL_NOT_FOUND(404, "Skill을 찾을 수 없습니다."),
    SECRET_NOT_FOUND(404, "Secret을 찾을 수 없습니다."),

    // === Validation ===
    VALIDATION_FAILED(400, "유효성 검증에 실패했습니다."),
    DUPLICATE_RESOURCE(409, "이미 존재하는 리소스입니다."),

    // === Squad ===
    ORCHESTRATOR_REQUIRED(400, "Squad에는 Orchestrator가 필수입니다."),
    INVALID_ORCHESTRATOR_ROLE(400, "Orchestrator 에이전트의 roleType이 'orchestrator'가 아닙니다."),

    // === Session ===
    INVALID_SESSION_STATE(400, "세션의 현재 상태에서 해당 작업을 수행할 수 없습니다."),

    // === Git ===
    INVALID_GIT_PROVIDER(400, "Git Provider를 판별할 수 없습니다."),
    INVALID_GIT_URL(400, "유효하지 않은 Git 저장소 URL입니다."),
    GIT_SECRET_NOT_FOUND(404, "지정된 Git Secret을 찾을 수 없습니다.");

    private final int httpStatus;
    private final String message;
}
