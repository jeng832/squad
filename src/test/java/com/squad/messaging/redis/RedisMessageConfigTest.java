package com.squad.messaging.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("RedisMessageConfig 통합 테스트")
class RedisMessageConfigTest extends RedisTestContainerConfig {

    @Autowired
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired
    private RedisSerializer<Object> messageSerializer;

    @Autowired
    private RedisConnectionFactory connectionFactory;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("RedisMessageListenerContainer 빈이 정상적으로 로딩된다")
    void listenerContainerBeanLoaded() {
        assertThat(redisMessageListenerContainer).isNotNull();
        assertThat(redisMessageListenerContainer.isRunning()).isTrue();
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

    @Test
    @DisplayName("실제 Redis에 연결할 수 있다")
    void redisConnectionWorks() {
        String pong = connectionFactory.getConnection().ping();

        assertThat(pong).isEqualTo("PONG");
    }

    @Test
    @DisplayName("Redis Pub/Sub으로 메시지를 발행하고 수신할 수 있다")
    void pubSubRoundTrip() throws InterruptedException {
        String channel = "test:pubsub";
        AwaitableMessageListener listener = new AwaitableMessageListener(channel);
        ChannelTopic topic = new ChannelTopic(channel);

        try {
            redisMessageListenerContainer.addMessageListener(listener, topic);
            assertThat(listener.awaitSubscribed(3, TimeUnit.SECONDS)).isTrue();

            redisTemplate.convertAndSend(channel, "hello-pubsub");

            assertThat(listener.awaitMessage(5, TimeUnit.SECONDS)).isTrue();
            assertThat(listener.getReceivedBody()).contains("hello-pubsub");
        } finally {
            redisMessageListenerContainer.removeMessageListener(listener, topic);
        }
    }
}
