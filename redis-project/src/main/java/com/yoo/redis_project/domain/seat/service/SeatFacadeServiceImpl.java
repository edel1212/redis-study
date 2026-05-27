package com.yoo.redis_project.domain.seat.service;

import com.yoo.redis_project.common.constants.RedisKeyConstants;
import com.yoo.redis_project.domain.seat.dto.SeatLockResponse;
import com.yoo.redis_project.domain.seat.entity.SeatEntity;
import com.yoo.redis_project.domain.seat.repository.SeatRepository;
import com.yoo.redis_project.domain.waiting.service.WaitingService;
import com.yoo.redis_project.exception.custom.BadRequestException;
import com.yoo.redis_project.exception.custom.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatFacadeServiceImpl implements SeatFacadeService {

    private final WaitingService waitingService;
    private final SeatLockService seatLockService;
    private final SeatRepository seatRepository;
    private final SeatService service;

    private static final long WAIT_TIME  = 3L;

    // redissonClient
    private final RedissonClient redissonClient;

    @Override
    public SeatLockResponse acquireWithValidation(Long concertId, Long seatId, Long userId, String token) {
        // ① 입장 토큰 검증 [RLock 밖]
        boolean isCorrectToken = waitingService.validateToken(concertId, userId, token);
        if (!isCorrectToken) {
            throw new BadRequestException("잘못된 접근의 사용자 입니다.");
        } // if

        // ② RLock 획득 - 좌석 ID를 기준으로 함
        RLock lock = redissonClient.getLock(RedisKeyConstants.SEAT_MUTEX.formatted(seatId));

        boolean acquired = false;

        try{
            // ③ RLock 잠금
            acquired = lock.tryLock(WAIT_TIME, -1 , TimeUnit.SECONDS);
            
            // 조건 처리
            if (!acquired) {
                return SeatLockResponse.fail(seatId, userId, "처리 중입니다. 잠시 후 다시 시도해주세요.");
            } // if

            // ③ DB 좌석 상태 확인
            SeatEntity seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new ResourceNotFoundException("좌석을 찾을 수 없습니다."));

            // ④  엔티티를 통해 좌석 상태 검증 진행
            seat.validateAvailable();

            // ④ Redis 락 (SET NX EX) 획득
            boolean lockResult = seatLockService.acquire(seatId, userId);

            // 락 획득 실패 시 상태 복구
            if(!lockResult) {
                return SeatLockResponse.fail(seatId, userId);
            } // if

            // 이부분은 따로 트랜잭션 처리가 필요함
            // ⑤ Redis 성공 후에야 DB 변경
            service.validateAndMarkHeld(seat);
            return SeatLockResponse.success(seatId, userId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[SeatFacade] RLock 인터럽트. seatId={}", seatId, e);
            return SeatLockResponse.fail(seatId, userId, "처리 중 오류가 발생했습니다.");
        } finally {
            // RLock 잠금과 정상적으로 lock을 잡고 있을 경우 lock 해제
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            } // if
        } // try - catch

    }
}
