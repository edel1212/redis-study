package com.yoo.redis_project.domain.seat.service;

import com.yoo.redis_project.domain.seat.entity.SeatEntity;

public interface SeatService {
    /**
     * 좌석 상태를 검증하고 HELD로 변경한다. (트랜잭션 내 실행)
     * <p>AVAILABLE 상태가 아니면 예외를 발생시킨다.
     * RLock 안에서 호출되어야 하며, 메서드 종료 시 커밋된다.</p>
     *
     * @param seat 좌석 entity
     * @throws com.yoo.redis_project.exception.custom.BadRequestException       이미 판매/점유된 좌석
     * @throws com.yoo.redis_project.exception.custom.ResourceNotFoundException 좌석을 찾을 수 없는 경우
     */
    void validateAndMarkHeld(SeatEntity seat);
}
