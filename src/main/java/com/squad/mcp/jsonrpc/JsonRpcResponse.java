package com.squad.mcp.jsonrpc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 응답 메시지.
 *
 * <p>서버가 클라이언트의 요청에 대해 반환하는 메시지이다.
 * 성공 시 {@code result}에 결과가, 실패 시 {@code error}에 에러 정보가 담긴다.</p>
 *
 * @param jsonrpc JSON-RPC 프로토콜 버전 (항상 "2.0")
 * @param id      요청 식별자 (요청의 id와 동일)
 * @param result  성공 결과 (nullable)
 * @param error   에러 정보 (nullable)
 * @see JsonRpcRequest
 * @see JsonRpcError
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcResponse(
        String jsonrpc,
        String id,
        JsonNode result,
        JsonRpcError error
) {

    /**
     * 에러 응답인지 확인한다.
     *
     * @return 에러 응답이면 {@code true}
     */
    public boolean isError() {
        return error != null;
    }
}
