package com.squad.messaging.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("RedisMessageConfig 테스트")
class RedisMessageConfigTest {

    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired
    private RedisSerializer<Object> messageSerializer;

    @Test
    @DisplayName("RedisMessageListenerContainer 빈이 정상적으로 로딩된다")
    void listenerContainerBeanLoaded() {
        assertThat(redisMessageListenerContainer).isNotNull();
    }

    @Test
    @DisplayName("messageSerializer 빈이 정상적으로 로딩된다")
    void messageSerializerBeanLoaded() {
        assertThat(messageSerializer).isNotNull();
    }

    @Test
    @DisplayName("messageSerializer로 객체를 직렬화/역직렬화할 수 있다")
    void messageSerializerRoundTrip() {
        String original = "테스트 메시지";

        byte[] serialized = messageSerializer.serialize(original);
        assertThat(serialized).isNotNull();

        Object deserialized = messageSerializer.deserialize(serialized);
        assertThat(deserialized).isEqualTo(original);
    }
}
