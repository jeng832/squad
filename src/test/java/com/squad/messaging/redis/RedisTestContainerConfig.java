package com.squad.messaging.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers 기반 Redis 테스트 공통 설정.
 *
 * <p>Redis 컨테이너를 기동하고, Spring의 Redis 연결 설정을 동적으로 주입한다.
 * 이 클래스를 상속하면 실제 Redis 인스턴스에서 통합 테스트를 수행할 수 있다.</p>
 *
 * <p>Testcontainers로 컨테이너 기동에 실패하면(예: Docker 미사용 CI 환경)
 * {@code localhost:6379}로 fallback한다. GitHub Actions에서는 서비스 컨테이너로
 * Redis를 제공하므로 fallback으로 정상 동작한다.</p>
 */
public abstract class RedisTestContainerConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisTestContainerConfig.class);
    private static final int REDIS_PORT = 6379;

    private static final GenericContainer<?> REDIS_CONTAINER;
    private static final boolean CONTAINER_RUNNING;

    static {
        GenericContainer<?> container = null;
        boolean running = false;
        try {
            container = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(REDIS_PORT);
            container.start();
            running = true;
        } catch (Exception e) {
            log.info("Testcontainers Redis 기동 실패, localhost:6379 fallback 사용: {}", e.getMessage());
        }
        REDIS_CONTAINER = container;
        CONTAINER_RUNNING = running;
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        if (CONTAINER_RUNNING) {
            registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
            registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(REDIS_PORT));
        } else {
            registry.add("spring.data.redis.host", () -> "localhost");
            registry.add("spring.data.redis.port", () -> REDIS_PORT);
        }
    }
}
