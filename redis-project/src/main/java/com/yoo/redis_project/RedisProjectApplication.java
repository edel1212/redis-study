package com.yoo.redis_project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;


// 스케줄링 추가
@EnableScheduling
// 추강화 공통 Entity 적용 추가
@EnableJpaAuditing
// ✅ 캐시 추상화 활성화 - 없을 경우 캐싱 사용 ❌
@EnableCaching
@SpringBootApplication
public class RedisProjectApplication {

	public static void main(String[] args) {
		SpringApplication.run(RedisProjectApplication.class, args);
	}

}
