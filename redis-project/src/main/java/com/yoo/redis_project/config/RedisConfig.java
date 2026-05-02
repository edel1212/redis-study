package com.yoo.redis_project.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yoo.redis_project.dto.PostDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
public class RedisConfig {

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
        ObjectMapper objectMapper = buildObjectMapper();

        // ✅ 기본 설정: 매칭되지 않은 캐시명에 적용 (fallback)
        RedisCacheConfiguration defaultConfig = baseConfig()
                .entryTtl(Duration.ofMinutes(10))
                // ✅ Value 는 JSON 설정
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(objectMapper))
                );

        // ✅ 캐시별 타입 고정 설정 - 해당 keys는 cache
        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                "post",    typedConfig(PostDto.class,    Duration.ofMinutes(30), objectMapper)
//                , "orders",   typedConfig(Order.class,   Duration.ofMinutes(5),  objectMapper)
//                , "products", typedConfig(Product.class, Duration.ofHours(1),    objectMapper)
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    /**
     * 공통 베이스: prefix, key 직렬화 등
     * <br/>
     * SpringBoot에서 자동으로 생성되는 key의 prefix가 "::" 형식이기에 ":"형식으로 변경함
     *
     * @return  the 공통 설정 RedisCacheConfiguration
     * */
    private RedisCacheConfiguration baseConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(cacheName -> cacheName + ":")
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .disableCachingNullValues(); // null 캐싱 방지 (선택, 권장)
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
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }


}
