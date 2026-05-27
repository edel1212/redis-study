package com.yoo.redis_project.domain.seat.service;

import com.yoo.redis_project.domain.seat.entity.SeatEntity;
import com.yoo.redis_project.domain.seat.repository.SeatRepository;
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
    public void validateAndMarkHeld(SeatEntity seat) {
        seat.validateAvailable();
        seat.markHeld();
    }
}
