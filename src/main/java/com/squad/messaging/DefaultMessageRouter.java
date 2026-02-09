package com.squad.messaging;

import com.squad.session.domain.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

/**
 * {@link MessageRouter}의 기본 구현체.
 *
 * <p>{@link MessageType}에 따라 {@link MessagePublisher}의 적절한 메서드를 호출하여
 * 메시지를 라우팅한다.</p>
 *
 * <p>라우팅 규칙:</p>
 * <ul>
 *   <li>{@code TASK_REQUEST}, {@code HELP_RESPONSE} → {@link MessagePublisher#sendToAgent}</li>
 *   <li>{@code TASK_RESULT}, {@code HELP_REQUEST} → {@link MessagePublisher#sendToOrchestrator}</li>
 *   <li>{@code SYSTEM} → {@link MessagePublisher#broadcast}</li>
 * </ul>
 *
 * <p>{@link MessagePublisher} 빈이 존재할 때만 활성화된다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(MessagePublisher.class)
public class DefaultMessageRouter implements MessageRouter {

    private final MessagePublisher messagePublisher;

    @Override
    public void route(SessionMessage message) {
        Assert.notNull(message, "message는 필수입니다");
        Assert.notNull(message.getType(), "메시지 타입은 필수입니다");
        Assert.notNull(message.getSessionId(), "sessionId는 필수입니다");

        switch (message.getType()) {
            case TASK_REQUEST, HELP_RESPONSE -> routeToAgent(message);
            case TASK_RESULT, HELP_REQUEST -> routeToOrchestrator(message);
            case SYSTEM -> broadcast(message);
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 메시지 타입: " + message.getType());
        }
    }

    private void routeToAgent(SessionMessage message) {
        Assert.notNull(message.getToAgentId(),
                message.getType() + " 메시지는 toAgentId가 필수입니다");

        log.debug("메시지 라우팅: type={}, session={}, from={}, to={}",
                message.getType(), message.getSessionId(),
                message.getFromAgentId(), message.getToAgentId());

        messagePublisher.sendToAgent(message);
    }

    private void routeToOrchestrator(SessionMessage message) {
        log.debug("메시지 라우팅: type={}, session={}, from={} → orchestrator",
                message.getType(), message.getSessionId(), message.getFromAgentId());

        messagePublisher.sendToOrchestrator(message);
    }

    private void broadcast(SessionMessage message) {
        log.debug("메시지 라우팅: type=SYSTEM, session={} → broadcast",
                message.getSessionId());

        messagePublisher.broadcast(message);
    }
}
