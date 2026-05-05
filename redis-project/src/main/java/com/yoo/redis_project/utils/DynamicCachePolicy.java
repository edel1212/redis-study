package com.yoo.redis_project.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class DynamicCachePolicy {
    private final StringRedisTemplate stringRedisTemplate;

    // Jitter ratios
    private double jitterRatio = .2;

    // 인기도 기준 (조회수)
    private static final long POPULAR_THRESHOLD = 10_000L;
    private static final long HOT_THRESHOLD = 100_000L;

    // TTL 정책
    private static final Duration TTL_HOT      = Duration.ofHours(2);    // 핫 공연
    private static final Duration TTL_POPULAR  = Duration.ofMinutes(30); // 인기 공연
    private static final Duration TTL_NORMAL   = Duration.ofMinutes(5);  // 일반 공연

    /**
     * 공연 ID 기준으로 TTL 결정
     */
    public Duration resolveTtl(Long concertId) {
        long viewCount = getViewCount(concertId);

        Duration baseTtl;
        if (viewCount >= HOT_THRESHOLD) {
            baseTtl = TTL_HOT;
        } else if (viewCount >= POPULAR_THRESHOLD) {
            baseTtl = TTL_POPULAR;
        } else {
            baseTtl = TTL_NORMAL;
        }// if - else

        log.debug("Resolved TTL for concert={}, viewCount={}, ttl={}",
                concertId, viewCount, baseTtl);

        // ✅ Jitter 적용 (Stampede 방지 - ② 의 결과물)
        return TtlUtils.jitter(baseTtl, jitterRatio);
    }

    /**
     * Redis 에서 조회수 가져오기
     */
    private long getViewCount(Long concertId) {
        String key = "concert:view-count:" + concertId;
        String count = stringRedisTemplate.opsForValue().get(key);
        return count == null ? 0L : Long.parseLong(count);
    }
}
