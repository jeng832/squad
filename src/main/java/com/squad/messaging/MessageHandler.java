package com.squad.messaging;

/**
 * 수신된 {@link SessionMessage}를 처리하는 핸들러.
 *
 * <p>구현체는 스레드 안전하게 작성되어야 하며,
 * 핸들러 내부에서 발생하는 예외는 메시징 인프라가 catch하여 로깅한다.</p>
 *
 * @see MessageSubscriber
 */
@FunctionalInterface
public interface MessageHandler {

    /**
     * 수신된 메시지를 처리한다.
     *
     * @param message 수신된 세션 메시지
     */
    void handle(SessionMessage message);
}
