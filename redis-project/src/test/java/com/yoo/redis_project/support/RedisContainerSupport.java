package com.yoo.redis_project.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class RedisContainerSupport {

    // 컨테이너 정보주입
    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7.2-alpine")
                    .withExposedPorts(6379);

    // 컨테이너 실행 후 동적으로 Spring 설정에 주입

    /**
     * 컨테이너 실행 후 설정 값 동적 주입
     *
     * @param registry the spring setting
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    // 한건의 테스트가 끝날때다 Redis 데이터 초기화
    @AfterEach
    void tearDown() {
        redisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushAll();
    }
}
