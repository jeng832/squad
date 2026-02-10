package com.squad.messaging.redis;

import com.squad.messaging.DefaultMessageRouter;
import com.squad.messaging.MessagePublisher;
import com.squad.messaging.MessageRouter;
import com.squad.messaging.MessageSubscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 메시징 provider 비활성화 시 관련 빈이 생성되지 않는 것을 검증하는 테스트.
 *
 * <p>{@code @SpringBootTest}로 전체 컨텍스트를 올리지 않고,
 * {@link ApplicationContextRunner}를 사용하여 메시징 관련 설정 클래스만
 * 대상으로 빈 등록 여부를 검증한다. 이렇게 하면 메시징과 무관한 빈
 * (예: {@code SessionExecutionService})의 의존성 문제에 영향받지 않는다.</p>
 */
@DisplayName("messaging provider 비활성화 테스트")
class RedisMessageProviderDisabledTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(
                    org.springframework.boot.autoconfigure.AutoConfigurations.of(
                            RedisAutoConfiguration.class
                    )
            )
            .withUserConfiguration(
                    RedisMessageConfig.class,
                    RedisMessagePublisher.class,
                    RedisMessageSubscriber.class,
                    DefaultMessageRouter.class
            )
            .withPropertyValues("squad.messaging.provider=none");

    @Test
    @DisplayName("provider가 redis가 아니면 MessagePublisher 빈이 생성되지 않는다")
    void publisherBeanNotCreated() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(MessagePublisher.class)
        );
    }

    @Test
    @DisplayName("provider가 redis가 아니면 MessageSubscriber 빈이 생성되지 않는다")
    void subscriberBeanNotCreated() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(MessageSubscriber.class)
        );
    }

    @Test
    @DisplayName("provider가 redis가 아니면 MessageRouter 빈이 생성되지 않는다")
    void routerBeanNotCreated() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(MessageRouter.class)
        );
    }

    @Test
    @DisplayName("provider가 redis가 아니면 RedisMessageListenerContainer 빈이 생성되지 않는다")
    void listenerContainerBeanNotCreated() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(RedisMessageListenerContainer.class)
        );
    }
}
