package com.squad.messaging.redis;

import com.squad.messaging.MessagePublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "squad.messaging.provider=none")
@DisplayName("messaging provider 비활성화 테스트")
class RedisMessageProviderDisabledTest {

    @Autowired(required = false)
    private MessagePublisher messagePublisher;

    @Autowired(required = false)
    private RedisMessageListenerContainer listenerContainer;

    @Test
    @DisplayName("provider가 redis가 아니면 MessagePublisher 빈이 생성되지 않는다")
    void publisherBeanNotCreated() {
        assertThat(messagePublisher).isNull();
    }

    @Test
    @DisplayName("provider가 redis가 아니면 RedisMessageListenerContainer 빈이 생성되지 않는다")
    void listenerContainerBeanNotCreated() {
        assertThat(listenerContainer).isNull();
    }
}
