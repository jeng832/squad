package com.squad.mcp.client;

import java.util.List;

/**
 * MCP tools/call 호출 결과.
 *
 * <p>도구 실행 결과를 content 목록과 에러 여부로 담는다.</p>
 *
 * @param content 결과 콘텐츠 목록
 * @param isError 도구 실행 에러 여부
 */
public record McpToolCallResult(
        List<Content> content,
        boolean isError
) {

    /**
     * 도구 실행 결과의 개별 콘텐츠 항목.
     *
     * @param type 콘텐츠 타입 (예: "text", "image")
     * @param text 콘텐츠 텍스트
     */
    public record Content(
            String type,
            String text
    ) {
    }
}
