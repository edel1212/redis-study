package com.yoo.redis_project.domain.booking.service;

import com.yoo.redis_project.domain.booking.dto.BookingResponse;
import com.yoo.redis_project.domain.seat.entity.SeatEntity;
import com.yoo.redis_project.domain.seat.repository.SeatRepository;
import com.yoo.redis_project.domain.seat.service.SeatLockService;
import com.yoo.redis_project.domain.waiting.service.WaitingService;
import com.yoo.redis_project.exception.custom.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {
    private final StringRedisTemplate stringRedisTemplate;
    private final WaitingService waitingService;
    private final SeatLockService seatLockService;
    private final SeatRepository seatRepository;

    @Transactional
    @Override
    public BookingResponse confirm(Long seatId, Long userId) {

        // 지정 좌석 조회 - 점유자 조회
        Optional<Long> owner = seatLockService.getLockOwner(seatId);

        if (owner.isEmpty() || !owner.get().equals(userId)) {
            return BookingResponse.fail(seatId, "좌석 점유 상태가 아니거나 소유자가 다릅니다.");
        } // if

        // DB 좌석 조회
        SeatEntity seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("좌석을 찾을 수 없습니다."));

        // 좌석 상태 업데이트
        seat.markAsSold();
        seatRepository.save(seat);

        // TODO : 해당 부분의 처리 방법은 Message Queue를 사용해서 처리하도록 변경이 필요
        
        // 입장 자리 반환 (entered + token 제거)
        Long concertId = seat.getConcert().getId();
        waitingService.releaseEntry(concertId, userId);
        
        // ⑤ 좌석 락 해제
        seatLockService.release(seatId, userId);

        log.info("예매 확정 seatId={}, userId={}", seatId, userId);
        return BookingResponse.success(seatId, userId);
    }
}
