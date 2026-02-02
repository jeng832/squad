package com.squad.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoleType {

    ORCHESTRATOR("orchestrator", "작업 분배 및 조율. 전체 흐름 관리. Squad의 중심"),
    WORKER("worker", "실제 작업 수행 (코드 작성, 분석 등)"),
    ANALYST("analyst", "결과 분석 및 종합"),
    SCRIBE("scribe", "과정 기록 및 문서화"),
    CUSTOM("custom", "사용자 정의 역할");

    private final String value;
    private final String description;

    public static RoleType fromValue(String value) {
        for (RoleType roleType : RoleType.values()) {
            if (roleType.value.equalsIgnoreCase(value)) {
                return roleType;
            }
        }
        throw new IllegalArgumentException("Unknown RoleType: " + value);
    }
}
