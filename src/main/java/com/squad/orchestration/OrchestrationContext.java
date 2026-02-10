package com.squad.orchestration;

import com.squad.agent.domain.Agent;
import com.squad.llm.model.LlmMessage;
import com.squad.messaging.Subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 하나의 세션에 대한 Orchestrator 실행 상태를 관리하는 컨텍스트.
 *
 * <p>메시지 수신 콜백이 별도 스레드에서 실행되므로,
 * 대화 히스토리({@code messages})와 대기 작업 수({@code pendingTasks})는
 * 스레드 안전하게 관리한다.</p>
 */
public class OrchestrationContext {

    private final Long sessionId;
    private final Agent orchestrator;
    private final Set<Agent> agents;
    private final CopyOnWriteArrayList<LlmMessage> messages;
    private final List<Subscription> subscriptions;
    private final AtomicInteger pendingTasks;

    private OrchestrationContext(Long sessionId, Agent orchestrator, Set<Agent> agents) {
        this.sessionId = sessionId;
        this.orchestrator = orchestrator;
        this.agents = agents;
        this.messages = new CopyOnWriteArrayList<>();
        this.subscriptions = Collections.synchronizedList(new ArrayList<>());
        this.pendingTasks = new AtomicInteger(0);
    }

    /**
     * 새로운 OrchestrationContext를 생성한다.
     *
     * @param sessionId    세션 ID
     * @param orchestrator Orchestrator Agent
     * @param agents       작업을 수행할 Agent 목록
     * @return 새 컨텍스트
     */
    public static OrchestrationContext of(Long sessionId, Agent orchestrator, Set<Agent> agents) {
        return new OrchestrationContext(sessionId, orchestrator, agents);
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Agent getOrchestrator() {
        return orchestrator;
    }

    public Set<Agent> getAgents() {
        return agents;
    }

    public List<LlmMessage> getMessages() {
        return List.copyOf(messages);
    }

    public void addMessage(LlmMessage message) {
        messages.add(message);
    }

    public void addSubscription(Subscription subscription) {
        subscriptions.add(subscription);
    }

    /**
     * 모든 구독을 해제한다.
     */
    public void unsubscribeAll() {
        for (Subscription subscription : subscriptions) {
            subscription.unsubscribe();
        }
    }

    public int incrementPendingTasks() {
        return pendingTasks.incrementAndGet();
    }

    public int decrementPendingTasks() {
        return pendingTasks.decrementAndGet();
    }

    public int getPendingTaskCount() {
        return pendingTasks.get();
    }
}
