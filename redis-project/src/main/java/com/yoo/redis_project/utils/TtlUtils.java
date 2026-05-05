package com.yoo.redis_project.utils;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

public class TtlUtils {

    private TtlUtils() {}

    /**
     * 기준 TTL 에 ±ratio 범위의 jitter 적용
     * @param base   기준 TTL
     * @param ratio  jitter 비율 (0.1 = ±10%)
     */
    public static Duration jitter(Duration base, double ratio) {
        long baseSeconds = base.getSeconds();
        long jitterRange = (long) (baseSeconds * ratio);
        if (jitterRange <= 0) return base;

        long delta = ThreadLocalRandom.current()
                .nextLong(-jitterRange, jitterRange + 1);
        return Duration.ofSeconds(baseSeconds + delta);
    }
}
