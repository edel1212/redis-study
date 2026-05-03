package com.yoo.redis_project.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RedisCacheHelper {
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * key 기준 redis에서 값을 가져옴
     *
     * @param key the key
     * @param type the class
     * @return the optional
     * @param <T>
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("캐시 역직렬화 실패", e);
        }
    }

    /**
     * String 저장
     *
     * @param key the key
     * @param value the value
     * @param <T> the class
     */
    public <T> void set(String key, T value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("캐시 직렬화 실패", e);
        }
    }

    /**
     * TTL을 포함하여 String 저장
     *
     * @param key the key
     * @param value the value
     * @param ttl the ttl
     * @param <T> the class
     */
    public <T> void set(String key, T value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("캐시 직렬화 실패", e);
        }
    }
}
