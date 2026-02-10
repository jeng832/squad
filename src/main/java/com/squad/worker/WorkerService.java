package com.squad.worker;

import com.squad.agent.domain.Agent;
import com.squad.llm.LlmProvider;
import com.squad.llm.LlmProviderFactory;
import com.squad.llm.model.*;
import com.squad.messaging.MessageRouter;
import com.squad.messaging.MessageSubscriber;
import com.squad.messaging.SessionMessage;
import com.squad.messaging.Subscription;
import com.squad.session.domain.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Worker Agent의 태스크 수신 및 LLM 호출을 담당하는 서비스.
 *
 * <p>세션이 시작되면 각 Worker Agent의 채널을 구독하고,
 * Orchestrator로부터 {@code TASK_REQUEST}를 수신하여 LLM을 호출한 뒤
 * 결과를 {@code TASK_RESULT}로 Orchestrator에게 반환한다.</p>
 *
 * <h3>작업 흐름:</h3>
 * <ol>
 *   <li>Worker Agent 채널 구독 (startWorker)</li>
 *   <li>TASK_REQUEST 수신</li>
 *   <li>Agent의 role 기반 시스템 프롬프트 구성</li>
 *   <li>LLM 호출 및 응답 수집</li>
 *   <li>TASK_RESULT를 Orchestrator에게 반환</li>
 * </ol>
 *
 * @see WorkerContext
 * @see MessageSubscriber
 * @see MessageRouter
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkerService {

    private final LlmProviderFactory llmProviderFactory;
    private final MessageRouter messageRouter;
    private final MessageSubscriber messageSubscriber;

    private final Map<String, WorkerContext> activeWorkers = new ConcurrentHashMap<>();

    /**
     * Worker Agent의 태스크 수신을 시작한다.
     *
     * <p>Agent 채널을 구독하여 TASK_REQUEST 메시지를 수신할 준비를 한다.
     * 반드시 Orchestrator의 메시지 발행 전에 호출해야 메시지 유실을 방지할 수 있다.</p>
     *
     * @param sessionId 세션 ID
     * @param agent     Worker Agent
     */
    public void startWorker(Long sessionId, Agent agent) {
        WorkerContext context = WorkerContext.of(sessionId, agent);
        String key = workerKey(sessionId, agent.getId());
        activeWorkers.put(key, context);

        Subscription subscription = messageSubscriber.subscribeToAgent(
                sessionId, agent.getId(),
                message -> handleMessage(context, message)
        );
        context.setSubscription(subscription);

        log.info("Worker 시작: sessionId={}, agentId={}, agentName={}",
                sessionId, agent.getId(), agent.getName());
    }

    /**
     * 특정 Worker Agent를 중지하고 구독을 해제한다.
     *
     * @param sessionId 세션 ID
     * @param agentId   Agent ID
     */
    public void stopWorker(Long sessionId, Long agentId) {
        String key = workerKey(sessionId, agentId);
        WorkerContext context = activeWorkers.remove(key);
        if (context != null) {
            context.unsubscribe();
            log.info("Worker 중지: sessionId={}, agentId={}", sessionId, agentId);
        }
    }

    /**
     * 세션에 속한 모든 Worker를 중지한다.
     *
     * @param sessionId 세션 ID
     */
    public void stopAllWorkers(Long sessionId) {
        String prefix = sessionId + ":";
        activeWorkers.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix)) {
                entry.getValue().unsubscribe();
                log.info("Worker 중지: sessionId={}, agentId={}",
                        sessionId, entry.getValue().getAgent().getId());
                return true;
            }
            return false;
        });
    }

    private void handleMessage(WorkerContext context, SessionMessage message) {
        try {
            if (message.getType() == MessageType.TASK_REQUEST) {
                processTaskRequest(context, message);
            } else {
                log.warn("Worker가 처리할 수 없는 메시지 타입: type={}, sessionId={}, agentId={}",
                        message.getType(), context.getSessionId(), context.getAgent().getId());
            }
        } catch (Exception e) {
            log.error("Worker 메시지 처리 실패: sessionId={}, agentId={}",
                    context.getSessionId(), context.getAgent().getId(), e);
            sendErrorResult(context, e);
        }
    }

    private void processTaskRequest(WorkerContext context, SessionMessage message) {
        log.debug("태스크 수신: sessionId={}, agentId={}, fromAgentId={}",
                context.getSessionId(), context.getAgent().getId(), message.getFromAgentId());

        context.addMessage(new LlmMessage("user", message.getContent()));

        String result = callLlm(context);
        sendTaskResult(context, result);
    }

    private String callLlm(WorkerContext context) {
        Agent agent = context.getAgent();
        LlmProvider provider = resolveProvider(agent);

        LlmRequest request = new LlmRequest(
                extractModel(agent),
                buildSystemPrompt(agent),
                context.getMessages(),
                null, null,
                List.of()
        );

        LlmResponse response = provider.sendMessage(request);

        if (response.content() != null && !response.content().isBlank()) {
            context.addMessage(new LlmMessage("assistant", response.content()));
            return response.content();
        }

        return "작업을 완료했으나 응답 내용이 없습니다.";
    }

    private void sendTaskResult(WorkerContext context, String result) {
        SessionMessage resultMessage = SessionMessage.of(
                context.getSessionId(),
                context.getAgent().getId(),
                null,
                MessageType.TASK_RESULT,
                result
        );

        messageRouter.route(resultMessage);

        log.debug("태스크 결과 반환: sessionId={}, agentId={}, resultLength={}",
                context.getSessionId(), context.getAgent().getId(), result.length());
    }

    private void sendErrorResult(WorkerContext context, Exception e) {
        String errorMessage = String.format("[오류] 작업 처리 중 오류 발생: %s", e.getMessage());

        SessionMessage errorResult = SessionMessage.of(
                context.getSessionId(),
                context.getAgent().getId(),
                null,
                MessageType.TASK_RESULT,
                errorMessage
        );

        try {
            messageRouter.route(errorResult);
        } catch (Exception routeException) {
            log.error("에러 결과 전송 실패: sessionId={}, agentId={}",
                    context.getSessionId(), context.getAgent().getId(), routeException);
        }
    }

    /**
     * Worker Agent의 시스템 프롬프트를 구성한다.
     *
     * @param agent Worker Agent
     * @return 시스템 프롬프트
     */
    String buildSystemPrompt(Agent agent) {
        return String.format(
                "당신은 %s입니다.\n\n당신의 역할: %s\n\n"
                        + "Orchestrator로부터 받은 작업을 수행하고 결과를 반환하세요.\n"
                        + "작업 결과는 명확하고 구조적으로 정리하여 반환하세요.",
                agent.getName(), agent.getRole()
        );
    }

    private LlmProvider resolveProvider(Agent agent) {
        String providerName = extractProviderName(agent);
        return llmProviderFactory.getProvider(providerName)
                .orElseThrow(() -> new IllegalStateException(
                        "LLM Provider를 찾을 수 없습니다: " + providerName));
    }

    private String extractProviderName(Agent agent) {
        Map<String, Object> llmConfig = agent.getLlmConfig();
        Object provider = llmConfig.get("provider");
        if (provider == null) {
            throw new IllegalStateException(
                    "Agent의 llmConfig에 provider가 설정되지 않았습니다: agentId=" + agent.getId());
        }
        return provider.toString();
    }

    private String extractModel(Agent agent) {
        Map<String, Object> llmConfig = agent.getLlmConfig();
        Object model = llmConfig.get("model");
        return model != null ? model.toString() : null;
    }

    private String workerKey(Long sessionId, Long agentId) {
        return sessionId + ":" + agentId;
    }
}
