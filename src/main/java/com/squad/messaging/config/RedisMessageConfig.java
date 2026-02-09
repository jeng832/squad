package com.squad.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Redis Pub/Sub 메시징 전용 설정 클래스.
 *
 * <p>{@link RedisMessageListenerContainer}를 구성하여 Redis 채널 구독을 지원하고,
 * Pub/Sub 메시지 직렬화를 위한 {@link RedisSerializer} 빈을 제공합니다.</p>
 *
 * <p>채널 구독은 세션 시작 시 동적으로 등록되며,
 * 세션 종료 시 구독이 해제됩니다.</p>
 *
 * @see com.squad.messaging.RedisChannelConstants
 */
@Configuration
public class RedisMessageConfig {

    /**
     * Redis Pub/Sub 리스너 컨테이너를 생성한다.
     *
     * <p>메시지 리스너(Subscriber)의 등록/해제를 관리하며,
     * 채널 구독은 세션 생명주기에 따라 동적으로 이루어진다.</p>
     *
     * @param connectionFactory Redis 연결 팩토리
     * @return 리스너 컨테이너
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    /**
     * Pub/Sub 메시지 직렬화/역직렬화에 사용할 {@link RedisSerializer}를 생성한다.
     *
     * <p>Jackson 기반 JSON 직렬화를 사용하여 메시지 객체를 직렬화한다.</p>
     *
     * @param objectMapper Jackson ObjectMapper
     * @return JSON 기반 Redis 직렬화기
     */
    @Bean
    public RedisSerializer<Object> messageSerializer(ObjectMapper objectMapper) {
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
}
