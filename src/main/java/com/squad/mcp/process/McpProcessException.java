package com.squad.mcp.process;

/**
 * MCP 프로세스 관련 예외.
 *
 * <p>MCP 서버 프로세스 시작, 통신, 종료 과정에서 발생하는
 * 예외를 나타낸다.</p>
 */
public class McpProcessException extends RuntimeException {

    public McpProcessException(String message, Throwable cause) {
        super(message, cause);
    }
}
