package com.squad.llm.tool.builtin;

/**
 * Built-in Tool 실행 대상(session/agent) 컨텍스트를 현재 스레드에 보관한다.
 */
public final class ToolExecutionContextHolder {

    private static final ThreadLocal<ToolExecutionContext> HOLDER = new ThreadLocal<>();

    private ToolExecutionContextHolder() {
    }

    public static void set(Long sessionId, Long agentId) {
        HOLDER.set(new ToolExecutionContext(sessionId, agentId));
    }

    public static ToolExecutionContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record ToolExecutionContext(Long sessionId, Long agentId) {
    }
}
