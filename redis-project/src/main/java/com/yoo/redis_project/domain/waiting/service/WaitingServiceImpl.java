package com.yoo.redis_project.domain.waiting.service;

import com.yoo.redis_project.common.constants.RedisKeyConstants;
import com.yoo.redis_project.domain.waiting.dto.EnqueueResult;
import com.yoo.redis_project.domain.waiting.dto.WaitingResponse;
import com.yoo.redis_project.exception.custom.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingServiceImpl implements WaitingService {

    // 대기 TTL
    private static final Duration QUEUE_TTL   = Duration.ofHours(2);
    // 입장 허용 TTL
    private static final Duration ENTERED_TTL = Duration.ofHours(2);
    // 토큰 시간 할당 (실제는 5분이지만 어유있는 시간 할당)
    private static final Duration TOKEN_TTL = Duration.ofMinutes(6);
    // 최대 입장 가능 사용자
    private static final int MAX_ENTRY_COUNT = 50;

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

        // 입장가능 여부 - score(만료 timestamp) 기반으로 유효성 판단
        Double expiryScore = redisTemplate.opsForZSet().score(enteredKey, userIdStr);
        // 값이 있으며 + 만료시간이 현재 시간보다 커야함
        boolean isEntered = expiryScore != null && expiryScore > System.currentTimeMillis();

        // 입장이 가능한 사용자일 경우
        if(isEntered){
            log.info("입장 가능한 사용자 : {}",userId);
            // 입장 가능 Token을 받아옴 - 스케줄링을 통해 생성된 Key
            String token = redisTemplate.opsForValue().get(tokenKey);
            // token 존재할 경우 응답
            if(token != null){
                return EnqueueResult.entered(token);
            }//if

            // score는 유효하나 token이 없는 엣지 케이스 - entered에서 제거
            redisTemplate.opsForZSet().remove(enteredKey, userIdStr);
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

        // 입장 가능자인지 확인 - score(만료 timestamp) 기반으로 유효성 판단
        Double expiryScore = redisTemplate.opsForZSet().score(enteredKey, userIdStr);
        boolean isEntered = expiryScore != null && expiryScore > System.currentTimeMillis();
        if(isEntered){
            // token 정보를 가져옴
            String token = redisTemplate.opsForValue().get(tokenKey);
            // key가 존재할 경우 응답
            if(token != null) return WaitingResponse.entered(token);

            // score는 유효하나 token이 없는 엣지 케이스 - entered에서 제거
            redisTemplate.opsForZSet().remove(enteredKey, userIdStr);
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

    @Override
    public void processEntry(Long concertId) {
        // 입장 가능 사용자
        String enteredKey = RedisKeyConstants.WAITING_ENTERED.formatted(concertId);
        // 대기열 사용자
        String queueKey   = RedisKeyConstants.WAITING_QUEUE.formatted(concertId);

        long now = System.currentTimeMillis();

        // Step 1. 만료된 entered 멤버 정리 (score ≤ now-1 인 멤버 범위 삭제)
        redisTemplate.opsForZSet().removeRangeByScore(enteredKey, 0, now - 1);

        // Step 2. 빈자리 계산
        // 유효한 입장 사용자 수 (score > now 인 멤버만 집계)
        Long currentEnteredCount = redisTemplate.opsForZSet()
                // key, 현재시간, 최대 값 범위
                // - count(K key, double min, double max);
                .count(enteredKey, now, Double.MAX_VALUE);
        long currentEntered = (currentEnteredCount != null) ? currentEnteredCount : 0L;
        // 입장이 가능한 사용자 수
        long available = MAX_ENTRY_COUNT - currentEntered;

        // 입장처리 가능한 사용자가 없음 (실제 좌석 예매 페이지 이동 공간 부족)
        if (available <= 0) return;

        // Step 3. 자리가 있을 경우 대기욜 사용자를 빼서 압장 사용자 등록
        Set<ZSetOperations.TypedTuple<String>> popped = redisTemplate.opsForZSet().popMin(queueKey, available);
        // 대기열 사용자가 없을 경우 skip
        if(popped == null || popped.isEmpty()) return;

        // 대기열 사용자에게 토큰 할당
        for (ZSetOperations.TypedTuple<String> tuple : popped) {
            String userId = tuple.getValue();
            // 예외 처리
            if (userId == null) continue;

            // 토큰 생성 및 할당
            String token = UUID.randomUUID().toString();
            String tokenKey = RedisKeyConstants.WAITING_TOKEN.formatted(
                    concertId, Long.parseLong(userId));

            // 토큰 먼저 → entered 나중 (race condition 방지)
            redisTemplate.opsForValue().set(tokenKey, token, TOKEN_TTL);
            // score = 토큰 만료 timestamp: 만료 여부를 score 비교로 판단
            double expiryScore = System.currentTimeMillis() + TOKEN_TTL.toMillis();
            redisTemplate.opsForZSet().add(enteredKey, userId, expiryScore);
        } // for

        // entered 키 안전망 TTL (Sorted Set이므로 개별 score로 만료 관리하지만 키 자체 정리용으로 유지)
        redisTemplate.expire(enteredKey, ENTERED_TTL);

        log.info("[스케줄링] : 입장 처리 완료 concertId={}, 입장 인원={}", concertId, popped.size());
    }

    @Override
    public void releaseEntry(Long concertId, Long userId) {
        String enteredKey = RedisKeyConstants.WAITING_ENTERED.formatted(concertId);
        String enteredTokenKey = RedisKeyConstants.WAITING_TOKEN.formatted(concertId, userId);

        // 입장 대상 제거
        redisTemplate.opsForZSet().remove(enteredKey, String.valueOf(userId));
        // 토큰 제거
        redisTemplate.delete(enteredTokenKey);

    }

    @Override
    public boolean validateToken(Long concertId, Long userId, String token) {
        String tokenKey = RedisKeyConstants.WAITING_TOKEN.formatted(concertId, userId);
        String enteredToken = redisTemplate.opsForValue().get(tokenKey);
        if(enteredToken == null) throw new BadRequestException("존재하지 않은 토큰입니다.");
        // redis에 저장된 토큰과 사용자 parameter token이 같은 경우 true
        return enteredToken.equals(token);
    }

}
