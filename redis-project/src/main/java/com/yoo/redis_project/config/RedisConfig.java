package com.yoo.redis_project.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory
    ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // 🔍 DTO 내 LocalTime 존재 시 "jackson.databind.exc.InvalidDefinitionException" 예외 방지
        ObjectMapper objectMapper = new ObjectMapper();
        // ✅ JavaTimeModule 등록
        objectMapper.registerModule(new JavaTimeModule());

        // ✅ LocalDateTime을 timestamps(숫자) 대신 문자열로 직렬화
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 설정이 간단하고 범용적이기에 GenericJackson2JsonRedisSerializer로 직렬화 설정을 한다.
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

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
}
