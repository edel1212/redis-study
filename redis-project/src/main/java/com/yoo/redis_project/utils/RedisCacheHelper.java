package com.yoo.redis_project.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
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

        } catch (DataAccessException e) {
            // Redis 서버 통신 장애 — 서비스 중단 없이 DB 폴백 유도
            log.warn("[RedisCacheHelper] Redis 통신 장애. key={}", key, e);
            return Optional.empty();

        } catch (Exception e) {
            // 역직렬화 실패 — 스키마 변경 등으로 깨진 캐시 자가 치유
            log.warn("[RedisCacheHelper] 역직렬화 실패. key={} 캐시 삭제 후 재생성 유도", key, e);
            safeDelete(key); // ← 깨진 캐시 즉시 제거
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

        } catch (DataAccessException e) {
            log.warn("[RedisCacheHelper] Redis 통신 장애. 캐시 저장 생략. key={}", key, e);

        } catch (Exception e) {
            log.warn("[RedisCacheHelper] 직렬화 실패. 캐시 저장 생략. key={}", key, e);
        }
    }

    /**
     * 캐시에서 리스트를 조회한다.
     *
     * <p>제네릭 컬렉션은 {@link Class} 로 역직렬화할 수 없으므로
     * Jackson {@link com.fasterxml.jackson.databind.type.CollectionType} 을 사용한다.
     * 역직렬화 실패 시 깨진 캐시를 자동 삭제하고 {@link Optional#empty()} 를 반환한다.
     *
     * @param key         Redis 키
     * @param elementType 리스트 원소 클래스 (예: SeatDto.class)
     * @param <T>         원소 타입
     * @return 캐시 히트 시 {@link Optional}로 감싼 리스트, 미스/장애 시 {@link Optional#empty()}
     */
    public <T> Optional<List<T>> getList(String key, Class<T> elementType) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            CollectionType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, elementType);
            return Optional.of(objectMapper.readValue(json, listType));

        } catch (DataAccessException e) {
            log.warn("[RedisCacheHelper] Redis 통신 장애. key={}", key, e);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("[RedisCacheHelper] 역직렬화 실패. key={} 캐시 삭제 후 재생성 유도", key, e);
            safeDelete(key);
            return Optional.empty();
        }
    }

    /**
     * 캐시를 명시적으로 삭제한다.
     *
     * <p>DB 갱신 후 캐시 무효화(Cache Aside 패턴)에 사용한다.
     * Redis 장애 시 예외 없이 WARN 로그만 남긴다 (Fail-Open).
     *
     * @param key 삭제할 Redis 키
     */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException e) {
            log.warn("[RedisCacheHelper] 캐시 삭제 실패. key={}", key, e);
        }
    }


    // ----------------------------------------------------------------
    // private
    // ----------------------------------------------------------------

    /**
     * 역직렬화 실패 시 내부에서 깨진 캐시를 조용히 제거한다.
     * 외부에 예외를 전파하지 않는다.
     */
    private void safeDelete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("[RedisCacheHelper] 깨진 캐시 삭제 실패. key={}", key, e);
        }
    }

}
