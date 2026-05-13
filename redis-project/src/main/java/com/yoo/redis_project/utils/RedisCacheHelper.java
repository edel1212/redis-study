package com.yoo.redis_project.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheHelper {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;


    /**
     * String 저장
     * <p>TTL이 없는 경우 사용을 권장하지 않음</p>
     *
     * @param key the key
     * @param value the value
     * @param <T> the class
     */
    @Deprecated
    public <T> void set(String key, T value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("캐시 직렬화 실패", e);
        }
    }

    /**
     * key 기준 Redis 에서 값을 가져온다.
     * <p>Fail-Open 정책: 역직렬화 실패 또는 Redis 통신 실패 시 빈 Optional 반환 (캐시 미스 처리).
     * <p>깨진 캐시는 자동으로 삭제 시도한다.
     *
     * @param key  Redis 키
     * @param type 역직렬화 타깃 클래스
     * @return 캐시 값 (없거나 실패 시 Optional.empty())
     */
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, type));

        } catch (JsonProcessingException e) {
            log.warn("캐시 역직렬화 실패 — key={}, 캐시 삭제 후 폴백", key, e);
            safeDelete(key);
            return Optional.empty();

        } catch (DataAccessException e) {   // Spring Data Redis 의 공통 부모
            log.warn("Redis 통신 실패 — key={}, 폴백", key, e);
            return Optional.empty();
        }
    }

    /**
     * TTL을 포함하여 String 저장.
     * <p>Fail-Open 정책: 직렬화/저장 실패는 로그만 남기고 무시 (캐시는 선택적 기능).
     *
     * @param key   Redis 키
     * @param value 저장할 값
     * @param ttl   만료 시간
     */
    public <T> void set(String key, T value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);

        } catch (JsonProcessingException e) {
            log.warn("캐시 직렬화 실패 — key={}, 저장 건너뜀", key, e);

        } catch (DataAccessException e) {
            log.warn("Redis 통신 실패 — key={}, 저장 건너뜀", key, e);
        }
    }

    // 깨진 key 삭제
    private void safeDelete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception ignored) {
            log.warn("깨진 캐시 삭제 실패 — key={}", key);
        }
    }

}
