package com.yoo.redis_project.domain.waiting.service;

import com.yoo.redis_project.common.constants.RedisKeyConstants;
import com.yoo.redis_project.domain.waiting.dto.EnqueueResult;
import com.yoo.redis_project.domain.waiting.dto.WaitingResponse;
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

            // token이 만료 됐을 경우 entered 대상에서 제거
            redisTemplate.opsForSet().remove(enteredKey, userIdStr);
            log.info("토큰 만료로 entered 제거 concertId={}, userId={}", concertId, userId);
            return EnqueueResult.expired();
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

    @Override
    public WaitingResponse getPosition(Long concertId, Long userId) {
        // Long -> String
        String userIdStr = String.valueOf(userId);

        // 콘서트 입장 가능자 확인 Key
        String enteredKey = RedisKeyConstants.WAITING_ENTERED.formatted(concertId);
        // 콘서트 대기열 key
        String queueKey = RedisKeyConstants.WAITING_QUEUE.formatted(concertId);
        // 입장 가능자 Key 목록
        String tokenKey = RedisKeyConstants.WAITING_TOKEN.formatted(concertId, userId);

        // 입장 가능자인지 확인
        Boolean isEntered = redisTemplate.opsForSet().isMember(enteredKey, userIdStr);
        if(Boolean.TRUE.equals(isEntered)){
            // token 정보를 가져옴
            String token = redisTemplate.opsForValue().get(tokenKey);
            // key가 존재할 경우 응답
            if(token != null) return WaitingResponse.entered(token);

            // token이 만료 됐을 경우 entered 대상에서 제거
            redisTemplate.opsForSet().remove(enteredKey, userIdStr);
            log.info("토큰 만료로 entered 제거 concertId={}, userId={}", concertId, userId);
            return EnqueueResult.expired().getResponse();
        } // if

        Long rank = redisTemplate.opsForZSet().rank(queueKey, userIdStr);

        // 대기열에 등록되지 않은 사용자임
        if (rank == null) {
            // 자동으로 사용자를 등록해주지 않는 이유는 API를 완벽하게 분리하여 의도를 명확하게 하기 위함
            return WaitingResponse.notInQueue();
        } // if

        return WaitingResponse.waiting(rank.intValue() + 1);
    }
}
