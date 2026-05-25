package com.yoo.redis_project.domain.seat.service;

import com.yoo.redis_project.domain.dto.SeatLockResponse;
import com.yoo.redis_project.domain.service.SeatLockService;
import com.yoo.redis_project.domain.waiting.service.WaitingService;
import com.yoo.redis_project.exception.custom.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatFacadeServiceImpl implements SeatFacadeService {

    private final WaitingService waitingService;
    private final SeatLockService seatLockService;

    @Override
    public SeatLockResponse acquireWithValidation(Long concertId, Long seatId, Long userId, String token) {
        // ① 입장 토큰 검증
        if (!waitingService.validateToken(concertId, userId, token)) {
            throw new BadRequestException("잘못된 접근의 사용자 입니다.");
        } // if

        // ② 좌석 락 시도
        boolean acquired = seatLockService.acquire(seatId, userId);

        // 점유 실패 시
        if (!acquired)  return SeatLockResponse.fail(seatId, userId);

        return SeatLockResponse.success(seatId, userId);
    }
}
