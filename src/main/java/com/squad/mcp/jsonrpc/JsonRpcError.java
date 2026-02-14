package com.squad.mcp.jsonrpc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 에러 객체.
 *
 * <p>요청 처리 중 에러가 발생했을 때 {@link JsonRpcResponse#error()}에 담긴다.</p>
 *
 * @param code    에러 코드
 * @param message 에러 메시지
 * @param data    추가 에러 데이터 (nullable)
 * @see JsonRpcResponse
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcError(
        int code,
        String message,
        JsonNode data
) {
}
