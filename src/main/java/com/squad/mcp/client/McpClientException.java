package com.squad.mcp.client;

import com.squad.mcp.jsonrpc.JsonRpcError;

import java.util.Optional;

/**
 * MCP 클라이언트 통신 및 프로토콜 에러를 나타내는 예외.
 *
 * <p>JSON-RPC 에러 응답, 통신 장애, 타임아웃 등
 * MCP 클라이언트 동작 중 발생하는 모든 예외를 포괄한다.</p>
 *
 * @see McpClient
 */
public class McpClientException extends RuntimeException {

    private final JsonRpcError jsonRpcError;

    /**
     * 메시지와 원인으로 예외를 생성한다.
     *
     * @param message 에러 메시지
     * @param cause   원인 예외 (nullable)
     */
    public McpClientException(String message, Throwable cause) {
        super(message, cause);
        this.jsonRpcError = null;
    }

    /**
     * 메시지만으로 예외를 생성한다.
     *
     * @param message 에러 메시지
     */
    public McpClientException(String message) {
        super(message);
        this.jsonRpcError = null;
    }

    /**
     * JSON-RPC 에러 응답으로부터 예외를 생성한다.
     *
     * @param message      에러 메시지
     * @param jsonRpcError JSON-RPC 에러 객체
     */
    public McpClientException(String message, JsonRpcError jsonRpcError) {
        super(message);
        this.jsonRpcError = jsonRpcError;
    }

    /**
     * JSON-RPC 에러 객체를 반환한다.
     *
     * @return JSON-RPC 에러 (없으면 empty)
     */
    public Optional<JsonRpcError> getJsonRpcError() {
        return Optional.ofNullable(jsonRpcError);
    }
}
