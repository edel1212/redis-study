package com.yoo.redis_project.domain.seat.service;

import com.yoo.redis_project.domain.seat.entity.SeatEntity;
import com.yoo.redis_project.domain.seat.repository.SeatRepository;
import com.yoo.redis_project.exception.custom.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService{
    private final SeatRepository seatRepository;

    @Transactional
    @Override
    public void validateAndMarkHeld(Long seatId) {
        SeatEntity seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("좌석을 찾을 수 없습니다."));
        seat.markHeld();
    }

    @Transactional
    @Override
    public void markAvailable(Long seatId) {
        SeatEntity seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("좌석을 찾을 수 없습니다."));

        seat.release();
    }
}
