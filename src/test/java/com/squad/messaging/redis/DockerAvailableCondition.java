package com.squad.messaging.redis;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Docker 데몬 가용 여부에 따라 테스트 실행을 결정하는 JUnit 5 조건.
 *
 * <p>Docker가 설치되지 않았거나 접근 불가능한 환경(예: CI)에서
 * Testcontainers 기반 통합 테스트를 자동으로 스킵한다.</p>
 */
public class DockerAvailableCondition implements ExecutionCondition {

    static final boolean DOCKER_AVAILABLE = checkDockerAvailable();

    /**
     * Docker 데몬이 사용 가능한지 반환한다.
     *
     * @return Docker 사용 가능 시 {@code true}
     */
    public static boolean isDockerAvailable() {
        return DOCKER_AVAILABLE;
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (DOCKER_AVAILABLE) {
            return ConditionEvaluationResult.enabled("Docker is available");
        }
        return ConditionEvaluationResult.disabled("Docker is not available — skipping Testcontainers test");
    }

    private static boolean checkDockerAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "info")
                    .redirectErrorStream(true)
                    .start();
            boolean exited = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return exited && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
