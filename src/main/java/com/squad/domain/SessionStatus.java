package com.squad.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SessionStatus {

    PENDING("pending", "세션 생성됨, 실행 대기 중"),
    RUNNING("running", "세션 실행 중"),
    COMPLETED("completed", "세션 정상 완료"),
    FAILED("failed", "세션 실패"),
    CANCELLED("cancelled", "세션 취소됨");

    private final String value;
    private final String description;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public boolean canCancel() {
        return this == PENDING || this == RUNNING;
    }
}
