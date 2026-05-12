package com.yoo.redis_project.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yoo.redis_project.dto.PostDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class RedisConfig {
    @Value("${spring.application.name}")
    private String appName;

    /**
     * ObjectMapper 공통 생성
     * <br/>
     * - RedisTemplate, RedisCacheManager : 모두에 동일하게 사용
     */
    private ObjectMapper buildObjectMapper() {
        // 🔍 DTO 내 LocalTime 존재 시 "jackson.databind.exc.InvalidDefinitionException" 예외 방지
        ObjectMapper objectMapper = new ObjectMapper();
        // ✅ JavaTimeModule 등록
        objectMapper.registerModule(new JavaTimeModule());
        // ✅ LocalDateTime을 timestamps(숫자) 대신 문자열로 직렬화
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // ☠️ ObjectMapper 변경 방식 :
        // - 타입 정보를 JSON 에 포함시켜야 역직렬화 시 원래 타입으로 복원 가능
        // 보안 및 분산 환경에 올지 못한 방향
//        objectMapper.activateDefaultTyping(
//                LaissezFaireSubTypeValidator.instance,
//                ObjectMapper.DefaultTyping.NON_FINAL,
//                JsonTypeInfo.As.PROPERTY
//        );

        return objectMapper;
    }

    /**
     * @Cacheable 이 사용하는 CacheManager
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = buildObjectMapper();

        // ✅ 정책 1: 일반 캐시 (null 차단, TTL 10분)
        RedisCacheConfiguration defaultConfig = baseConfig()
                .entryTtl(Duration.ofMinutes(10))
                // ✅ Value 는 JSON 설정
                .serializeValuesWith(genericValueSerializer(objectMapper))
                // null 차딘
                .disableCachingNullValues();

        // ✅ 정책 2: null 허용 캐시 (Cache Penetration 방어, TTL 30초)
        RedisCacheConfiguration nullSafeConfig = baseConfig()
                .entryTtl(Duration.ofSeconds(30))
                .serializeValuesWith(genericValueSerializer(objectMapper));

        // null 허용 캐시들
        List<String> nullSafeCaches = List.of(
                "post-existence",
                "user-existence"
        );

        // key 별 캐시 저장 Map
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        // Null 허용 캐시 추가
        nullSafeCaches.forEach(name -> cacheConfigs.put(name, nullSafeConfig));

        // 타입 고정 캐시 (별도 정책) - 필요의 경우 typedConfig 수정을 통해 null 허용 구분 값 추가
        cacheConfigs.put("post", typedConfig(PostDto.class, Duration.ofMinutes(30), objectMapper));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                // ✅ transaction 이 종료 후 Redis에 반영
                .transactionAware()
                .build();
    }

    /**
     * 공통 베이스: prefix, key 직렬화, null 캐싱 방지 등
     * <br/>
     * SpringBoot에서 자동으로 생성되는 key의 prefix가 "::" 형식이기에 ":"형식으로 변경함
     *
     * @return  the 공통 설정 RedisCacheConfiguration
     * */
    private RedisCacheConfiguration baseConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(cacheName -> appName + ":" + cacheName + ":")
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                ;
    }

    /**
     * 특정 타입 전용 캐시 설정
     * @param type the Object -> class 변환할 구조
     * @param ttl   the 해당 매핑되는 key의 TTL 설정
     * @param objectMapper the 사용될 ObjectMapper
     *
     * @return  the 지정 class의 RedisCacheConfiguration
     * @param <T> the 변환할 class generic
     */
    private <T> RedisCacheConfiguration typedConfig(
            Class<T> type, Duration ttl, ObjectMapper objectMapper) {

        Jackson2JsonRedisSerializer<T> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, type);

        return baseConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }

    /**
     * value 직렬화
     *
     * @param om the objectMapper
     * @return the RedisSerializationContext.SerializationPair
     */
    private RedisSerializationContext.SerializationPair<Object> genericValueSerializer(
            ObjectMapper om) {
        return RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer(om));
    }

}
