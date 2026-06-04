package com.yoo.redis_project.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 127.0.0.1 루프백 이슈를 우회하기 위해 명시적으로 "redis://localhost:6379" 주입
        config
                .useSentinelServers()                           // SentinelServers로 구성하였기에 변경 
                .setMasterName("cache-redis")                   // ★ conf 이름과 일치시켜야 함
                .addSentinelAddress(
                        "redis://localhost:26379",
                        "redis://localhost:26380",
                        "redis://localhost:26381")
                // master에서만 읽기로 고정
                .setReadMode(ReadMode.MASTER)
                // 운영에선 true 맞지만 학습용은 localhost 환경이기에 false 진행
                .setCheckSentinelsList(false);

        return Redisson.create(config);
    }


}
