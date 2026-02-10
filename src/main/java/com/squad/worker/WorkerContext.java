package com.squad.worker;

import com.squad.agent.domain.Agent;
import com.squad.llm.model.LlmMessage;
import com.squad.messaging.Subscription;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 하나의 Worker Agent에 대한 실행 상태를 관리하는 컨텍스트.
 *
 * <p>메시지 수신 콜백이 별도 스레드에서 실행되므로,
 * 대화 히스토리({@code messages})는 스레드 안전하게 관리한다.</p>
 *
 * @see WorkerService
 */
public class WorkerContext {

    private final Long sessionId;
    private final Agent agent;
    private final CopyOnWriteArrayList<LlmMessage> messages;
    private Subscription subscription;

    private WorkerContext(Long sessionId, Agent agent) {
        this.sessionId = sessionId;
        this.agent = agent;
        this.messages = new CopyOnWriteArrayList<>();
    }

    /**
     * 새로운 WorkerContext를 생성한다.
     *
     * @param sessionId 세션 ID
     * @param agent     Worker Agent
     * @return 새 컨텍스트
     */
    public static WorkerContext of(Long sessionId, Agent agent) {
        return new WorkerContext(sessionId, agent);
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Agent getAgent() {
        return agent;
    }

    public List<LlmMessage> getMessages() {
        return List.copyOf(messages);
    }

    public void addMessage(LlmMessage message) {
        messages.add(message);
    }

    public void setSubscription(Subscription subscription) {
        this.subscription = subscription;
    }

    /**
     * 구독을 해제한다.
     */
    public void unsubscribe() {
        if (subscription != null) {
            subscription.unsubscribe();
        }
    }
}
