package com.yoo.redis_project.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * ObjectMapper 공통 생성 (RedisTemplate, RedisCacheManager 모두에 동일하게 사용)
     */
    private ObjectMapper buildObjectMapper() {
        // 🔍 DTO 내 LocalTime 존재 시 "jackson.databind.exc.InvalidDefinitionException" 예외 방지
        ObjectMapper objectMapper = new ObjectMapper();
        // ✅ JavaTimeModule 등록
        objectMapper.registerModule(new JavaTimeModule());
        // ✅ LocalDateTime을 timestamps(숫자) 대신 문자열로 직렬화
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // ✅ 핵심 : 타입 정보를 JSON 에 포함시켜야 역직렬화 시 원래 타입으로 복원 가능
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        return objectMapper;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate( RedisConnectionFactory connectionFactory ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // 설정이 간단하고 범용적이기에 GenericJackson2JsonRedisSerializer로 직렬화 설정을 한다.
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(buildObjectMapper());

        // Key 직렬화 (String)
        // 최상위 Key
        template.setKeySerializer(stringSerializer);
        // Hash 내부 Field명
        template.setHashKeySerializer(stringSerializer);

        // Value 직렬화 (JSON)
        // 최상위 Key
        template.setValueSerializer(jsonSerializer);
        // Hash 내부 Field명
        template.setHashValueSerializer(jsonSerializer);

        return template;
    }

    /**
     * @Cacheable 이 사용하는 CacheManager
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(buildObjectMapper());

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                // ✅ prefix 컨벤션을 "::" → ":" 로 변경 (Redis 표준 컨벤션)
                .computePrefixWith(cacheName -> cacheName + ":")
                // ✅ Key 는 String
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer())
                )
                // ✅ Value 는 JSON
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(jsonSerializer)
                );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
