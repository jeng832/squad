package com.squad.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageType {

    TASK_REQUEST("task_request", "작업 요청"),
    TASK_RESULT("task_result", "작업 결과"),
    HELP_REQUEST("help_request", "도움 요청"),
    HELP_RESPONSE("help_response", "도움 응답"),
    STATUS_UPDATE("status_update", "상태 업데이트"),
    ERROR("error", "에러 메시지"),
    SYSTEM("system", "시스템 메시지");

    private final String value;
    private final String description;
}
