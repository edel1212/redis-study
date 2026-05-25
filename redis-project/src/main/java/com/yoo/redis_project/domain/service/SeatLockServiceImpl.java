package com.yoo.redis_project.domain.service;

import com.yoo.redis_project.common.constants.RedisKeyConstants;
import com.yoo.redis_project.domain.waiting.service.WaitingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatLockServiceImpl implements SeatLockService {
    private final StringRedisTemplate stringRedisTemplate;
    private final WaitingService waitingService;

    /** 임시 점유 유지 시간 — 5분 (결제 완료 예상 시간) */
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    // lock 획득
    @Override
    public boolean acquire(Long seatId, Long userId) {
        String key   = RedisKeyConstants.SEAT_LOCK.formatted(seatId);
        String value = String.valueOf(userId); // 소유자 식별값
        try {
            // value 값으로 요청자의 user 식별값을 주입
            Boolean acquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, value, LOCK_TTL);

            boolean result = Boolean.TRUE.equals(acquired);
            if (result) {
                log.info("[SeatLock] 락 획득 성공. seatId={} userId={}", seatId, userId);
            } else {
                log.info("[SeatLock] 락 획득 실패 (이미 점유 중). seatId={} userId={}", seatId, userId);
            } // if - else
            return result;

        } catch (DataAccessException e) {
            // Redis 장애 시 Fail-Closed — 락 획득 실패로 처리
            // 좌석 중복 점유가 락 획득 실패보다 위험하므로
            log.warn("[SeatLock] Redis 장애. 락 획득 실패로 처리. seatId={}", seatId, e);
            return false;
        } // try - catch
    }

    // lock 해제
    // 現구조 문제점 ) GET + DEL 비원자적
    // - 문제:
    //  GET 후 DEL 사이에 TTL 만료 + 다른 사용자 락 획득 시 → 다른 사용자 락을 DEL 하는 사고 발생 가능
    // - 완전한 해결책:
    //  Lua 스크립트로 GET + DEL 원자적 처리 또는 redisson 사용 (5단계 Redisson에서 다룸)
    @Override
    public boolean release(Long seatId, Long userId) {
        String key   = RedisKeyConstants.SEAT_LOCK.formatted(seatId);
        String value = String.valueOf(userId);

        try {
            // 요청 좌석의 정보 확인 (사용자 식별ID - value)
            String owner = stringRedisTemplate.opsForValue().get(key);

            // 락 없음 (이미 만료)
            if (owner == null) {
                log.info("[SeatLock] 락 없음 (만료됨). seatId={}", seatId);
                return false;
            } // if

            // 소유자 불일치 — 다른 사용자의 락
            if (!owner.equals(value)) {
                log.warn("[SeatLock] 좌석 소유자 불일치. seatId={} 요청userId={} 실제owner={}",
                        seatId, userId, owner);
                return false;
            } // if

            // 소유자 일치 → 해제
            stringRedisTemplate.delete(key);
            log.info("[SeatLock] 락 해제 완료. seatId={} userId={}", seatId, userId);
            return true;

        } catch (DataAccessException e) {
            log.warn("[SeatLock] Redis 장애. 락 해제 실패. seatId={}", seatId, e);
            return false;
        } // try - catch
    }

    // 락 소유자 조회
    @Override
    public Optional<Long> getLockOwner(Long seatId) {
        String key = RedisKeyConstants.SEAT_LOCK.formatted(seatId);
        try {
            String owner = stringRedisTemplate.opsForValue().get(key);
            if (owner == null) {
                return Optional.empty();
            } // if
            return Optional.of(Long.parseLong(owner));

        } catch (DataAccessException e) {
            log.warn("[SeatLock] Redis 장애. 소유자 조회 실패. seatId={}", seatId, e);
            return Optional.empty();
        } // try - catch
    }
}
