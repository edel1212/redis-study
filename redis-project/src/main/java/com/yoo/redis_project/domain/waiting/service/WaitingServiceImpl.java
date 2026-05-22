package com.yoo.redis_project.domain.waiting.service;

import com.yoo.redis_project.common.constants.RedisKeyConstants;
import com.yoo.redis_project.domain.waiting.dto.EnqueueResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingServiceImpl implements WaitingService {

    // 대기 TTL
    private static final Duration QUEUE_TTL   = Duration.ofHours(2);
    // 입장 허용 TTL
    private static final Duration ENTERED_TTL = Duration.ofHours(2);

    private final StringRedisTemplate redisTemplate;

    @Override
    public EnqueueResult enqueue(Long concertId, Long userId) {
        // 사용자 식별 ID String 변환 - 조회 및 등록에는 String만 가능함 -  cannot be cast to class java.lang.String 에러 발생
        String userIdStr = String.valueOf(userId);
        // 입장 가능한 사용자 목록 Key
        String enteredKey = RedisKeyConstants.WAITING_ENTERED.formatted(concertId);
        // 대기 사용자 사용자 목록 Key
        String queueKey   = RedisKeyConstants.WAITING_QUEUE.formatted(concertId);
        // 대기 사용자 사용자 목록 Key
        String tokenKey   = RedisKeyConstants.WAITING_TOKEN.formatted(concertId, userId);

        // 입장가능 여부
        Boolean isEntered = redisTemplate.opsForSet().isMember(enteredKey, userIdStr);

        // 입장이 가능한 사용자일 경우
        if(Boolean.TRUE.equals(isEntered)){
            log.info("입장 가능한 사용자 : {}",userId);
            // 입장 가능 Token을 받아옴 - 스케줄링을 통해 생성된 Key
            String token = redisTemplate.opsForValue().get(tokenKey);
            // token 존재할 경우 응답
            if(token != null){
                return EnqueueResult.entered(token);
            }//if

            // 토큰 만료 → 입장은 했지만 토큰 없음 (EXPIRED 상태)
            // 학습 범위에서는 재발급 없이 만료 안내
            log.warn("입장 토큰 만료 concertId={}, userId={}", concertId, userId);
            return EnqueueResult.entered(null);
        } // if

        // 1. 가상 룸 입장 - 대기열 등록
        double visitTime = System.currentTimeMillis();

        // 대기열 등록 - NX 방식 등록
        Boolean added = redisTemplate.opsForZSet()
                .addIfAbsent(queueKey, userIdStr, visitTime);

        // 사용자의 현재 대기 순서 조회 - null 일 경우는 존재하지 않으나 예외 처리 진행
        Long rank = redisTemplate.opsForZSet().rank(queueKey, userIdStr);
        if(rank == null){
            log.warn("ZRANK null — concertId={}, userId={}", concertId, userId);
            throw new RuntimeException("rank 조회에 실패하였습니다.");
        } // if

        // 2 최초 진입 시 대기열 키 TTL 설정
        if (Boolean.TRUE.equals(added)) {
            log.info("신규 대기열 등록 사용자 : {}",userId);
            redisTemplate.expire(queueKey, QUEUE_TTL);
            return EnqueueResult.newWaiting(rank.intValue() + 1);
        } //if

        // 이미 존재하는 대기열 사용자일 경우
        return EnqueueResult.existingWaiting(rank.intValue() + 1);
    }
}
