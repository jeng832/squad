package com.squad.messaging.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
        String message = "hello-pubsub";

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();

        MessageListener listener = (msg, pattern) -> {
            Object deserialized = messageSerializer.deserialize(msg.getBody());
            received.set(String.valueOf(deserialized));
            latch.countDown();
        };

        ChannelTopic topic = new ChannelTopic(channel);
        redisMessageListenerContainer.addMessageListener(listener, topic);

        Thread.sleep(200);

        redisTemplate.convertAndSend(channel, message);

        boolean completed = latch.await(5, TimeUnit.SECONDS);

        redisMessageListenerContainer.removeMessageListener(listener, topic);

        assertThat(completed).isTrue();
        assertThat(received.get()).isEqualTo(message);
    }
}
