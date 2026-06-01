package com.yoo.redis_project.domain.booking.service;

import com.yoo.redis_project.domain.seat.entity.SeatEntity;
import com.yoo.redis_project.domain.seat.repository.SeatRepository;
import com.yoo.redis_project.exception.custom.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {
    private final SeatRepository seatRepository;

    @Transactional
    @Override
    public Long markAsSold(Long seatId) {
        SeatEntity seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("좌석을 찾을 수 없습니다."));

        seat.markAsSold();   // dirty checking — save() 불필요

        return seat.getConcert().getId();   // 트랜잭션 안에서 미리 추출 (Lazy 회피)
    }
}
