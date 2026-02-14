package com.squad.messaging.redis;

import org.junit.jupiter.api.extension.ExtendWith;
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
 * <p>Docker가 설치되지 않은 환경에서는 {@link DockerAvailableCondition}에 의해
 * 테스트가 자동으로 스킵된다.</p>
 */
@ExtendWith(DockerAvailableCondition.class)
public abstract class RedisTestContainerConfig {

    private static final int REDIS_PORT = 6379;

    private static volatile GenericContainer<?> redisContainer;

    @SuppressWarnings("resource")
    static GenericContainer<?> getRedisContainer() {
        if (redisContainer == null) {
            synchronized (RedisTestContainerConfig.class) {
                if (redisContainer == null) {
                    redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                            .withExposedPorts(REDIS_PORT);
                    redisContainer.start();
                }
            }
        }
        return redisContainer;
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        GenericContainer<?> container = getRedisContainer();
        registry.add("spring.data.redis.host", container::getHost);
        registry.add("spring.data.redis.port", () -> container.getMappedPort(REDIS_PORT));
    }
}
