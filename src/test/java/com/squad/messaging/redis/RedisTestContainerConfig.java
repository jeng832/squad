package com.squad.messaging.redis;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers 기반 Redis 테스트 공통 설정.
 *
 * <p>Redis 컨테이너를 기동하고, Spring의 Redis 연결 설정을 동적으로 주입한다.
 * 이 클래스를 상속하면 실제 Redis 인스턴스에서 통합 테스트를 수행할 수 있다.</p>
 */
public abstract class RedisTestContainerConfig {

    private static final int REDIS_PORT = 6379;

    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(REDIS_PORT);

    static {
        REDIS_CONTAINER.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(REDIS_PORT));
    }
}
