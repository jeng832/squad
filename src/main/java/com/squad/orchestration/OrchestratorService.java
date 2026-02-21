package com.squad.orchestration;

import com.squad.agent.domain.Agent;
import com.squad.llm.LlmProvider;
import com.squad.llm.LlmProviderFactory;
import com.squad.llm.model.*;
import com.squad.messaging.MessageRouter;
import com.squad.messaging.MessageSubscriber;
import com.squad.messaging.SessionMessage;
import com.squad.messaging.Subscription;
import com.squad.monitoring.SessionEventPublisher;
import com.squad.session.domain.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrator의 작업 분배 로직을 담당하는 서비스.
 *
 * <p>세션이 시작되면 Orchestrator 채널을 구독하고, 사용자 프롬프트를 수신하여
 * LLM을 호출한다. LLM의 tool_use 응답(delegate_task, complete_session)을 해석하여
 * Agent에게 작업을 분배하거나 세션을 완료한다.</p>
 *
 * <h3>작업 흐름:</h3>
 * <ol>
 *   <li>사용자 프롬프트 수신 (TASK_REQUEST)</li>
 *   <li>LLM 호출 — 사용 가능한 Agent 정보와 tool 정의를 함께 전달</li>
 *   <li>LLM 응답의 tool_use에 따라 작업 분배 또는 세션 완료</li>
 *   <li>Agent로부터 TASK_RESULT 수신 시 LLM 재호출하여 다음 액션 결정</li>
 * </ol>
 *
 * @see OrchestrationContext
 * @see MessageSubscriber
 * @see MessageRouter
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {
    private static final int MAX_DELEGATION_ATTEMPTS = 12;
    private static final int MAX_SAME_TASK_ATTEMPTS = 2;

    private final LlmProviderFactory llmProviderFactory;
    private final MessageRouter messageRouter;
    private final MessageSubscriber messageSubscriber;
    private final SessionEventPublisher sessionEventPublisher;

    private final Map<Long, OrchestrationContext> activeOrchestrations = new ConcurrentHashMap<>();
    private final Map<Long, SessionCompleteHandler> completeHandlers = new ConcurrentHashMap<>();

    /**
     * 세션에 대한 Orchestrator 작업 분배를 시작한다.
     *
     * <p>Orchestrator의 Agent 채널과 Orchestrator 채널을 구독하여
     * 메시지 수신 시 자동으로 작업 분배 로직을 실행한다.</p>
     *
     * <p>반드시 사용자 프롬프트 발행 전에 호출해야 한다.
     * 구독이 설정되기 전에 메시지가 발행되면 유실될 수 있다.</p>
     *
     * @param sessionId       세션 ID
     * @param orchestrator    Orchestrator Agent
     * @param agents          작업 대상 Agent 목록
     * @param completeHandler 세션 완료 시 호출할 콜백
     */
    public void startOrchestration(Long sessionId, Agent orchestrator, Set<Agent> agents,
                                   SessionCompleteHandler completeHandler) {
        completeHandlers.put(sessionId, completeHandler);
        OrchestrationContext context = OrchestrationContext.of(sessionId, orchestrator, agents);
        activeOrchestrations.put(sessionId, context);

        Subscription agentSub = messageSubscriber.subscribeToAgent(
                sessionId, orchestrator.getId(),
                message -> handleMessage(context, message)
        );
        context.addSubscription(agentSub);

        Subscription orchestratorSub = messageSubscriber.subscribeToOrchestrator(
                sessionId,
                message -> handleMessage(context, message)
        );
        context.addSubscription(orchestratorSub);

        log.info("Orchestration 시작: sessionId={}, orchestratorId={}, agents={}",
                sessionId, orchestrator.getId(), agents.size());
    }

    /**
     * 세션의 Orchestration을 중지하고 구독을 해제한다.
     *
     * @param sessionId 세션 ID
     */
    public void stopOrchestration(Long sessionId) {
        completeHandlers.remove(sessionId);
        OrchestrationContext context = activeOrchestrations.remove(sessionId);
        if (context != null) {
            context.unsubscribeAll();
            log.info("Orchestration 중지: sessionId={}", sessionId);
        }
    }

    private void handleMessage(OrchestrationContext context, SessionMessage message) {
        try {
            switch (message.getType()) {
                case TASK_REQUEST -> processTaskRequest(context, message);
                case TASK_RESULT -> processTaskResult(context, message);
                default -> log.warn("Orchestrator가 처리할 수 없는 메시지 타입: type={}, sessionId={}",
                        message.getType(), context.getSessionId());
            }
        } catch (Exception e) {
            log.error("Orchestration 메시지 처리 실패: sessionId={}, type={}",
                    context.getSessionId(), message.getType(), e);
        }
    }

    private void processTaskRequest(OrchestrationContext context, SessionMessage message) {
        log.debug("사용자 프롬프트 수신: sessionId={}", context.getSessionId());

        context.addMessage(new LlmMessage("user", message.getContent()));
        callLlmAndProcess(context);
    }

    private void processTaskResult(OrchestrationContext context, SessionMessage message) {
        log.debug("Agent 결과 수신: sessionId={}, fromAgentId={}",
                context.getSessionId(), message.getFromAgentId());

        sessionEventPublisher.publishAgentStatus(
                context.getSessionId(), message.getFromAgentId(),
                findAgentName(context, message.getFromAgentId()), "IDLE");
        sessionEventPublisher.publishMessage(
                context.getSessionId(), message.getFromAgentId(), context.getOrchestrator().getId(),
                MessageType.TASK_RESULT.name(), message.getContent());

        String resultContent = String.format("[Agent %d 작업 결과]\n%s",
                message.getFromAgentId(), message.getContent());
        context.addMessage(new LlmMessage("user", resultContent));

        int remaining = context.decrementPendingTasks();
        if (remaining < 0) {
            log.warn("예상치 못한 TASK_RESULT 수신, 카운터 리셋: sessionId={}, fromAgentId={}",
                    context.getSessionId(), message.getFromAgentId());
            context.resetPendingTasks();
            return;
        }

        if (remaining == 0) {
            callLlmAndProcess(context);
        }
    }

    private void callLlmAndProcess(OrchestrationContext context) {
        LlmProvider provider = resolveProvider(context.getOrchestrator());

        LlmRequest request = new LlmRequest(
                extractModel(context.getOrchestrator()),
                buildSystemPrompt(context.getOrchestrator(), context.getAgents()),
                context.getMessages(),
                null, null,
                buildTools(context.getAgents())
        );

        LlmResponse response = provider.sendMessage(request);

        if (response.content() != null && !response.content().isBlank()) {
            context.addMessage(new LlmMessage("assistant", response.content()));
        }

        if (response.toolCalls() != null && !response.toolCalls().isEmpty()) {
            for (LlmToolCall toolCall : response.toolCalls()) {
                processToolCall(context, toolCall);
            }
        } else {
            log.info("LLM이 tool 호출 없이 응답: sessionId={}, content={}",
                    context.getSessionId(),
                    response.content() != null ? response.content().substring(0, Math.min(100, response.content().length())) : "null");
        }
    }

    private void processToolCall(OrchestrationContext context, LlmToolCall toolCall) {
        switch (toolCall.name()) {
            case "delegate_task" -> delegateTask(context, toolCall);
            case "complete_session" -> completeSession(context, toolCall);
            default -> log.warn("알 수 없는 tool 호출: name={}, sessionId={}",
                    toolCall.name(), context.getSessionId());
        }
    }

    private void delegateTask(OrchestrationContext context, LlmToolCall toolCall) {
        Long agentId = ((Number) toolCall.arguments().get("agent_id")).longValue();
        String task = (String) toolCall.arguments().get("task");

        if (!isValidAgent(context, agentId)) {
            log.warn("유효하지 않은 Agent ID로 작업 분배 시도: sessionId={}, agentId={}",
                    context.getSessionId(), agentId);
            return;
        }

        String delegationKey = buildDelegationKey(agentId, task);
        int totalAttempts = context.incrementDelegationAttempts();
        int sameTaskAttempts = context.incrementDelegationByKey(delegationKey);
        if (totalAttempts > MAX_DELEGATION_ATTEMPTS || sameTaskAttempts > MAX_SAME_TASK_ATTEMPTS) {
            String reason = String.format("반복 위임 제한 초과(total=%d, sameTask=%d, agentId=%d)",
                    totalAttempts, sameTaskAttempts, agentId);
            log.warn("Orchestration 중단 가드 동작: sessionId={}, {}", context.getSessionId(), reason);
            forceCompleteByGuard(context, reason);
            return;
        }

        SessionMessage taskMessage = SessionMessage.of(
                context.getSessionId(),
                context.getOrchestrator().getId(),
                agentId,
                MessageType.TASK_REQUEST,
                task
        );

        messageRouter.route(taskMessage);
        context.incrementPendingTasks();

        sessionEventPublisher.publishAgentStatus(
                context.getSessionId(), agentId, findAgentName(context, agentId), "WORKING");
        sessionEventPublisher.publishMessage(
                context.getSessionId(), context.getOrchestrator().getId(), agentId,
                MessageType.TASK_REQUEST.name(), task);

        log.debug("작업 분배: sessionId={}, toAgentId={}, task={}",
                context.getSessionId(), agentId,
                task.substring(0, Math.min(50, task.length())));
    }

    private String buildDelegationKey(Long agentId, String task) {
        String normalizedTask = task == null ? "" : task.replaceAll("\\s+", " ").trim().toLowerCase();
        return agentId + "::" + normalizedTask;
    }

    private String findAgentName(OrchestrationContext context, Long agentId) {
        return context.getAgents().stream()
                .filter(agent -> agent.getId().equals(agentId))
                .map(Agent::getName)
                .findFirst()
                .orElse("Unknown");
    }

    private boolean isValidAgent(OrchestrationContext context, Long agentId) {
        return context.getAgents().stream()
                .anyMatch(agent -> agent.getId().equals(agentId));
    }

    private void completeSession(OrchestrationContext context, LlmToolCall toolCall) {
        String result = (String) toolCall.arguments().get("result");
        Long sessionId = context.getSessionId();

        log.info("Orchestrator 완료 결정: sessionId={}", sessionId);

        SessionMessage completeMessage = SessionMessage.of(
                sessionId,
                context.getOrchestrator().getId(),
                null,
                MessageType.SYSTEM,
                result
        );
        messageRouter.route(completeMessage);

        SessionCompleteHandler handler = completeHandlers.get(sessionId);
        stopOrchestration(sessionId);

        if (handler != null) {
            handler.onSessionComplete(sessionId, result);
        }
    }

    private void forceCompleteByGuard(OrchestrationContext context, String reason) {
        String result = buildGuardFallbackResult(context, reason);
        completeSession(context, new LlmToolCall(
                "guard-complete",
                "complete_session",
                Map.of("result", result)
        ));
    }

    private String buildGuardFallbackResult(OrchestrationContext context, String reason) {
        List<String> allAgentResults = context.getMessages().stream()
                .filter(m -> "user".equals(m.role()) && m.content() != null && m.content().startsWith("[Agent "))
                .map(LlmMessage::content)
                .toList();
        int from = Math.max(0, allAgentResults.size() - 6);
        List<String> recentAgentResults = allAgentResults.subList(from, allAgentResults.size());

        StringBuilder sb = new StringBuilder();
        sb.append("세션이 반복 위임으로 진행 중단되어 강제 종료되었습니다.\n");
        sb.append("원인: ").append(reason).append("\n\n");
        if (!recentAgentResults.isEmpty()) {
            sb.append("최근 에이전트 결과 요약:\n");
            for (String result : recentAgentResults) {
                sb.append("- ")
                        .append(result.replaceAll("\\s+", " ").trim(), 0, Math.min(180, result.length()))
                        .append("\n");
            }
        } else {
            sb.append("수집된 에이전트 결과가 없어 요약할 내용이 없습니다.\n");
        }
        return sb.toString();
    }

    private LlmProvider resolveProvider(Agent orchestrator) {
        String providerName = extractProviderName(orchestrator);
        return llmProviderFactory.getProvider(providerName)
                .orElseThrow(() -> new IllegalStateException(
                        "LLM Provider를 찾을 수 없습니다: " + providerName));
    }

    private String extractProviderName(Agent orchestrator) {
        Map<String, Object> llmConfig = orchestrator.getLlmConfig();
        Object provider = llmConfig.get("provider");
        if (provider == null) {
            throw new IllegalStateException("Agent의 llmConfig에 provider가 설정되지 않았습니다: agentId=" + orchestrator.getId());
        }
        return provider.toString();
    }

    private String extractModel(Agent orchestrator) {
        Map<String, Object> llmConfig = orchestrator.getLlmConfig();
        Object model = llmConfig.get("model");
        return model != null ? model.toString() : null;
    }

    String buildSystemPrompt(Agent orchestrator, Set<Agent> agents) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 Orchestrator입니다. 사용자의 요청을 분석하여 적절한 Agent에게 작업을 분배하세요.\n\n");
        sb.append("당신의 역할: ").append(orchestrator.getRole()).append("\n\n");
        sb.append("사용 가능한 Agent 목록:\n");

        for (Agent agent : agents) {
            sb.append(String.format("- Agent ID: %d, 이름: %s, 역할 유형: %s, 역할: %s\n",
                    agent.getId(), agent.getName(), agent.getRoleType().name(), agent.getRole()));
        }

        sb.append("\n작업을 분배하려면 delegate_task 도구를 사용하세요.");
        sb.append("\n모든 작업이 완료되면 complete_session 도구로 최종 결과를 반환하세요.");

        return sb.toString();
    }

    List<LlmTool> buildTools(Set<Agent> agents) {
        List<LlmTool> tools = new ArrayList<>();

        Map<String, Object> delegateSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "agent_id", Map.of(
                                "type", "integer",
                                "description", "작업을 위임할 Agent의 ID"
                        ),
                        "task", Map.of(
                                "type", "string",
                                "description", "Agent에게 전달할 작업 내용"
                        )
                ),
                "required", List.of("agent_id", "task")
        );
        tools.add(new LlmTool("delegate_task", "특정 Agent에게 작업을 위임합니다.", delegateSchema));

        Map<String, Object> completeSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "result", Map.of(
                                "type", "string",
                                "description", "세션의 최종 결과"
                        )
                ),
                "required", List.of("result")
        );
        tools.add(new LlmTool("complete_session", "세션을 완료하고 최종 결과를 반환합니다.", completeSchema));

        return tools;
    }
}
