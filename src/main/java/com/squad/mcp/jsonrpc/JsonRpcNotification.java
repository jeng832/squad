package com.squad.mcp.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 알림 메시지.
 *
 * <p>응답을 기대하지 않는 메시지이다. 요청과 달리 {@code id}가 없다.</p>
 *
 * @param jsonrpc JSON-RPC 프로토콜 버전 (항상 "2.0")
 * @param method  메서드 이름
 * @param params  파라미터 (nullable)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcNotification(
        String jsonrpc,
        String method,
        JsonNode params
) {

    private static final String JSON_RPC_VERSION = "2.0";

    /**
     * 파라미터가 있는 알림을 생성한다.
     *
     * @param method 메서드 이름
     * @param params 파라미터
     * @return JSON-RPC 알림
     */
    public static JsonRpcNotification of(String method, JsonNode params) {
        return new JsonRpcNotification(JSON_RPC_VERSION, method, params);
    }

    /**
     * 파라미터가 없는 알림을 생성한다.
     *
     * @param method 메서드 이름
     * @return JSON-RPC 알림
     */
    public static JsonRpcNotification of(String method) {
        return new JsonRpcNotification(JSON_RPC_VERSION, method, null);
    }
}
