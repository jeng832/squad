package com.squad.messaging;

/**
 * 메시지 채널 구독을 나타내는 인터페이스.
 *
 * <p>{@link MessageSubscriber}가 반환하며, 호출자가 구독 lifecycle을 관리할 수 있도록 한다.
 * 구독 해제는 멱등(idempotent)하게 동작해야 하며, 중복 호출 시 안전하게 무시된다.</p>
 *
 * @see MessageSubscriber
 */
public interface Subscription {

    /**
     * 구독을 해제한다.
     *
     * <p>이미 해제된 상태에서 다시 호출해도 예외 없이 무시된다(멱등).</p>
     */
    void unsubscribe();
}
