package com.yoo.redis_project.domain.booking.service;

import com.yoo.redis_project.domain.booking.dto.BookingResponse;
import com.yoo.redis_project.domain.seat.service.SeatLockService;
import com.yoo.redis_project.domain.waiting.service.WaitingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingFacadeServiceImpl implements BookingFacadeService {
    private final BookingService bookingService;     // DB 트랜잭션
    private final SeatLockService seatLockService;    // Redis 락
    private final WaitingService waitingService;      // 대기열

    @Override
    public BookingResponse confirm(Long seatId, Long userId) {

        // 지정 좌석 조회 - 점유자 조회
        Optional<Long> owner = seatLockService.getLockOwner(seatId);

        if (owner.isEmpty() || !owner.get().equals(userId)) {
            return BookingResponse.fail(seatId, "좌석 점유 상태가 아니거나 소유자가 다릅니다.");
        } // if

        // 자리 선점 및 대상 ConcertID 반환 - 구조적 문제 학습용이기에 단일 기반으로 잡혀있음
        Long concertId = bookingService.markAsSold(seatId);

        // 3) 커밋 성공 후 Redis 정리 (best-effort, 자가 치유 대상)
        try {
            waitingService.releaseEntry(concertId, userId);
            seatLockService.release(seatId, userId);
        } catch (Exception e) {
            // 좌석은 이미 SOLD로 확정됨(source of truth = DB).
            // lock(TTL) / token(TTL) / entered(removeRangeByScore 스케줄러)로 자가 치유되므로
            // 정리 실패를 예매 실패로 전파하지 않는다.
            log.error("예매 확정 후 Redis 정리 실패(자가 치유 대상) seatId={}, userId={}",
                    seatId, userId, e);
        }

        log.info("예매 확정 seatId={}, userId={}", seatId, userId);
        return BookingResponse.success(seatId, userId);
    }
}
