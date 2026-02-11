package com.squad.mcp.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 요청 메시지.
 *
 * <p>클라이언트가 서버에게 특정 메서드 호출을 요청할 때 사용한다.
 * {@code id}를 포함하여 응답과 매칭한다.</p>
 *
 * @param jsonrpc JSON-RPC 프로토콜 버전 (항상 "2.0")
 * @param id      요청 식별자
 * @param method  호출할 메서드 이름
 * @param params  메서드 파라미터 (nullable)
 * @see JsonRpcResponse
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcRequest(
        String jsonrpc,
        String id,
        String method,
        JsonNode params
) {

    private static final String JSON_RPC_VERSION = "2.0";

    /**
     * 파라미터가 있는 JSON-RPC 요청을 생성한다.
     *
     * @param id     요청 식별자
     * @param method 호출할 메서드 이름
     * @param params 메서드 파라미터
     * @return JSON-RPC 요청
     */
    public static JsonRpcRequest of(String id, String method, JsonNode params) {
        return new JsonRpcRequest(JSON_RPC_VERSION, id, method, params);
    }

    /**
     * 파라미터가 없는 JSON-RPC 요청을 생성한다.
     *
     * @param id     요청 식별자
     * @param method 호출할 메서드 이름
     * @return JSON-RPC 요청
     */
    public static JsonRpcRequest of(String id, String method) {
        return new JsonRpcRequest(JSON_RPC_VERSION, id, method, null);
    }
}
