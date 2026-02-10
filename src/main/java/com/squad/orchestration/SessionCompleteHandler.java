package com.squad.orchestration;

/**
 * 세션 완료 시 후처리를 수행하는 콜백 인터페이스.
 *
 * <p>{@link OrchestratorService}가 {@code complete_session} tool 호출을 감지하면
 * 이 핸들러를 통해 세션 완료 처리를 위임한다.</p>
 *
 * <p>순환 의존을 방지하기 위해 {@link OrchestratorService}는
 * 이 인터페이스에만 의존하고, 구현체는 세션 서비스 계층에서 제공한다.</p>
 *
 * @see OrchestratorService
 */
@FunctionalInterface
public interface SessionCompleteHandler {

    /**
     * 세션 완료 후처리를 수행한다.
     *
     * <p>세션 상태를 COMPLETED로 전이하고, Worker 구독 정리,
     * Container 정리 등의 리소스 해제를 수행한다.</p>
     *
     * @param sessionId 완료된 세션 ID
     * @param result    최종 결과
     */
    void onSessionComplete(Long sessionId, String result);
}
