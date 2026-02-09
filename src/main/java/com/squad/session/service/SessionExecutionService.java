package com.squad.session.service;

import com.squad.agent.domain.Agent;
import com.squad.agent.runner.ContainerLifecycleManager;
import com.squad.common.exception.ErrorCode;
import com.squad.common.exception.NotFoundException;
import com.squad.common.exception.ValidationException;
import com.squad.messaging.MessagePublisher;
import com.squad.messaging.SessionMessage;
import com.squad.session.domain.Session;
import com.squad.session.domain.SessionStatus;
import com.squad.session.dto.SessionResponse;
import com.squad.session.repository.SessionRepository;
import com.squad.squad.domain.Squad;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 세션 실행 lifecycle을 관리하는 서비스.
 *
 * <p>세션 시작 흐름을 조율한다:</p>
 * <ol>
 *   <li>세션 조회 및 상태 검증 (PENDING 상태만 시작 가능)</li>
 *   <li>Squad의 모든 Agent에 대해 Container 생성 및 시작</li>
 *   <li>세션 상태를 RUNNING으로 전이</li>
 *   <li>Orchestrator에게 사용자 프롬프트 전달</li>
 * </ol>
 *
 * <p>Container 시작 실패 시 이미 시작된 Container를 정리하고 예외를 전파한다.</p>
 *
 * @see Session
 * @see ContainerLifecycleManager
 * @see MessagePublisher
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionExecutionService {

    private final SessionRepository sessionRepository;
    private final ContainerLifecycleManager containerLifecycleManager;
    private final MessagePublisher messagePublisher;

    /**
     * 세션을 시작한다.
     *
     * <p>PENDING 상태의 세션만 시작할 수 있으며, Squad에 속한 모든 Agent의
     * Container를 생성/시작한 후 Orchestrator에게 사용자 프롬프트를 전달한다.</p>
     *
     * @param sessionId 시작할 세션 ID
     * @return 시작된 세션 정보
     * @throws NotFoundException   세션이 존재하지 않는 경우
     * @throws ValidationException PENDING 상태가 아닌 경우
     */
    @Transactional
    public SessionResponse start(Long sessionId) {
        Session session = findAndValidate(sessionId);
        Squad squad = session.getSquad();
        Agent orchestrator = squad.getOrchestrator();

        List<String> startedContainerIds = startContainers(session, squad);

        try {
            session.start();
            sessionRepository.flush();
        } catch (Exception e) {
            cleanupContainers(startedContainerIds);
            throw e;
        }

        sendPromptToOrchestrator(session, orchestrator);

        log.info("세션 시작 완료: sessionId={}, squadId={}, agents={}",
                session.getId(), squad.getId(), startedContainerIds.size());

        return SessionResponse.from(session);
    }

    private Session findAndValidate(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SESSION_NOT_FOUND));

        if (session.getStatus() != SessionStatus.PENDING) {
            throw new ValidationException(ErrorCode.INVALID_SESSION_STATE,
                    "PENDING 상태의 세션만 시작할 수 있습니다. 현재 상태: " + session.getStatus());
        }

        return session;
    }

    private List<String> startContainers(Session session, Squad squad) {
        String sessionIdStr = String.valueOf(session.getId());
        Agent orchestrator = squad.getOrchestrator();
        Set<Agent> agents = squad.getAgents();
        List<String> startedContainerIds = new ArrayList<>();

        try {
            String orchestratorContainerId = startAgentContainer(sessionIdStr, orchestrator);
            startedContainerIds.add(orchestratorContainerId);

            for (Agent agent : agents) {
                String containerId = startAgentContainer(sessionIdStr, agent);
                startedContainerIds.add(containerId);
            }
        } catch (Exception e) {
            log.error("Container 시작 실패, 정리 수행: sessionId={}", session.getId(), e);
            cleanupContainers(startedContainerIds);
            throw new RuntimeException("Agent Container 시작에 실패했습니다.", e);
        }

        return startedContainerIds;
    }

    private String startAgentContainer(String sessionId, Agent agent) {
        String agentIdStr = String.valueOf(agent.getId());
        List<String> env = buildContainerEnv(sessionId, agent);

        log.debug("Container 시작: sessionId={}, agentId={}, agentName={}",
                sessionId, agentIdStr, agent.getName());

        return containerLifecycleManager.createAndStartContainer(sessionId, agentIdStr, env);
    }

    private List<String> buildContainerEnv(String sessionId, Agent agent) {
        return List.of(
                "SESSION_ID=" + sessionId,
                "AGENT_ID=" + agent.getId(),
                "AGENT_NAME=" + agent.getName(),
                "AGENT_ROLE=" + agent.getRole(),
                "AGENT_ROLE_TYPE=" + agent.getRoleType().name()
        );
    }

    private void sendPromptToOrchestrator(Session session, Agent orchestrator) {
        SessionMessage promptMessage = SessionMessage.of(
                session.getId(),
                null,
                orchestrator.getId(),
                com.squad.session.domain.MessageType.TASK_REQUEST,
                session.getUserPrompt()
        );

        messagePublisher.sendToAgent(promptMessage);

        log.debug("Orchestrator에 프롬프트 전달: sessionId={}, orchestratorId={}",
                session.getId(), orchestrator.getId());
    }

    private void cleanupContainers(List<String> containerIds) {
        for (String containerId : containerIds) {
            try {
                containerLifecycleManager.stopAndRemoveContainer(containerId);
            } catch (Exception e) {
                log.warn("Container 정리 실패: containerId={}", containerId, e);
            }
        }
    }
}
