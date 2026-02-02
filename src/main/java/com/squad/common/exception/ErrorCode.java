package com.squad.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common Errors
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다."),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "C002", "잘못된 타입입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C003", "리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C004", "허용되지 않은 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C005", "서버 내부 오류가 발생했습니다."),

    // Agent Errors
    AGENT_NOT_FOUND(HttpStatus.NOT_FOUND, "A001", "에이전트를 찾을 수 없습니다."),
    AGENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "A002", "이미 존재하는 에이전트입니다."),
    AGENT_IN_USE(HttpStatus.CONFLICT, "A003", "사용 중인 에이전트는 삭제할 수 없습니다."),
    INVALID_ROLE_TYPE(HttpStatus.BAD_REQUEST, "A004", "잘못된 역할 타입입니다."),

    // Squad Errors
    SQUAD_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "Squad를 찾을 수 없습니다."),
    SQUAD_ALREADY_EXISTS(HttpStatus.CONFLICT, "S002", "이미 존재하는 Squad입니다."),
    ORCHESTRATOR_REQUIRED(HttpStatus.BAD_REQUEST, "S003", "Squad에는 Orchestrator가 필수입니다."),
    INVALID_ORCHESTRATOR(HttpStatus.BAD_REQUEST, "S004", "Orchestrator의 역할 타입이 올바르지 않습니다."),
    SQUAD_IN_USE(HttpStatus.CONFLICT, "S005", "세션이 진행 중인 Squad는 수정/삭제할 수 없습니다."),

    // Session Errors
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SS001", "세션을 찾을 수 없습니다."),
    SESSION_ALREADY_RUNNING(HttpStatus.CONFLICT, "SS002", "이미 실행 중인 세션입니다."),
    SESSION_ALREADY_COMPLETED(HttpStatus.CONFLICT, "SS003", "이미 완료된 세션입니다."),
    SESSION_CANNOT_CANCEL(HttpStatus.CONFLICT, "SS004", "취소할 수 없는 세션 상태입니다."),

    // MCP Errors
    MCP_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "MCP를 찾을 수 없습니다."),
    MCP_ALREADY_EXISTS(HttpStatus.CONFLICT, "M002", "이미 존재하는 MCP입니다."),
    MCP_IN_USE(HttpStatus.CONFLICT, "M003", "사용 중인 MCP는 삭제할 수 없습니다."),
    INVALID_MCP_CONFIG(HttpStatus.BAD_REQUEST, "M004", "잘못된 MCP 설정입니다."),

    // Skill Errors
    SKILL_NOT_FOUND(HttpStatus.NOT_FOUND, "SK001", "Skill을 찾을 수 없습니다."),
    SKILL_ALREADY_EXISTS(HttpStatus.CONFLICT, "SK002", "이미 존재하는 Skill입니다."),
    SKILL_IN_USE(HttpStatus.CONFLICT, "SK003", "사용 중인 Skill은 삭제할 수 없습니다."),

    // Secret Errors
    SECRET_NOT_FOUND(HttpStatus.NOT_FOUND, "SEC001", "Secret을 찾을 수 없습니다."),
    SECRET_ALREADY_EXISTS(HttpStatus.CONFLICT, "SEC002", "이미 존재하는 Secret입니다."),
    SECRET_DECRYPTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SEC003", "Secret 복호화에 실패했습니다."),

    // LLM Errors
    LLM_API_ERROR(HttpStatus.BAD_GATEWAY, "L001", "LLM API 호출 중 오류가 발생했습니다."),
    LLM_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "L002", "LLM API 호출 한도를 초과했습니다."),
    UNSUPPORTED_LLM_PROVIDER(HttpStatus.BAD_REQUEST, "L003", "지원하지 않는 LLM Provider입니다."),

    // Container Errors
    CONTAINER_START_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CT001", "컨테이너 시작에 실패했습니다."),
    CONTAINER_STOP_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CT002", "컨테이너 종료에 실패했습니다."),
    CONTAINER_NOT_FOUND(HttpStatus.NOT_FOUND, "CT003", "컨테이너를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
