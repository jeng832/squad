package com.squad.orchestration;

import com.squad.agent.domain.Agent;
import com.squad.llm.model.LlmMessage;
import com.squad.messaging.Subscription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private final AtomicInteger delegationAttempts;
    private final Map<String, AtomicInteger> delegationByKey;
    private final Map<Long, Queue<String>> recentTasksByAgent;

    private OrchestrationContext(Long sessionId, Agent orchestrator, Set<Agent> agents) {
        this.sessionId = sessionId;
        this.orchestrator = orchestrator;
        this.agents = agents;
        this.messages = new CopyOnWriteArrayList<>();
        this.subscriptions = Collections.synchronizedList(new ArrayList<>());
        this.pendingTasks = new AtomicInteger(0);
        this.delegationAttempts = new AtomicInteger(0);
        this.delegationByKey = new ConcurrentHashMap<>();
        this.recentTasksByAgent = new ConcurrentHashMap<>();
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

    /**
     * 대기 작업 수를 0으로 리셋한다.
     *
     * <p>예상치 못한 TASK_RESULT로 카운터가 음수가 된 경우 복구에 사용한다.</p>
     */
    public void resetPendingTasks() {
        pendingTasks.set(0);
    }

    public int getPendingTaskCount() {
        return pendingTasks.get();
    }

    public int incrementDelegationAttempts() {
        return delegationAttempts.incrementAndGet();
    }

    public int incrementDelegationByKey(String key) {
        return delegationByKey.computeIfAbsent(key, ignored -> new AtomicInteger(0))
                .incrementAndGet();
    }

    public boolean isSimilarTaskAndTrack(Long agentId, String normalizedTask,
                                         double threshold, int maxHistory) {
        Queue<String> history = recentTasksByAgent.computeIfAbsent(
                agentId, ignored -> new LinkedList<>());
        synchronized (history) {
            for (String previous : history) {
                if (taskSimilarity(previous, normalizedTask) >= threshold) {
                    return true;
                }
            }
            history.offer(normalizedTask);
            while (history.size() > maxHistory) {
                history.poll();
            }
            return false;
        }
    }

    private double taskSimilarity(String a, String b) {
        if (a.equals(b)) {
            return 1.0;
        }
        int maxLength = Math.max(a.length(), b.length());
        if (maxLength == 0) {
            return 1.0;
        }
        int distance = levenshteinDistance(a, b);
        double editSimilarity = 1.0 - ((double) distance / maxLength);
        double tokenSimilarity = jaccardTokenSimilarity(a, b);
        return Math.max(editSimilarity, tokenSimilarity);
    }

    private double jaccardTokenSimilarity(String a, String b) {
        Set<String> aTokens = new HashSet<>(List.of(a.split("\\s+")));
        Set<String> bTokens = new HashSet<>(List.of(b.split("\\s+")));
        aTokens.removeIf(String::isBlank);
        bTokens.removeIf(String::isBlank);
        if (aTokens.isEmpty() && bTokens.isEmpty()) {
            return 1.0;
        }
        Set<String> intersection = new HashSet<>(aTokens);
        intersection.retainAll(bTokens);
        Set<String> union = new HashSet<>(aTokens);
        union.addAll(bTokens);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private int levenshteinDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
