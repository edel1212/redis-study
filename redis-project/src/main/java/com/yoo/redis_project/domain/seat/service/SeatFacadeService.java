package com.yoo.redis_project.domain.seat.service;

import com.yoo.redis_project.domain.dto.SeatLockResponse;

public interface SeatFacadeService {
    /**
     * 입장 토큰 검증 후 좌석 점유를 시도한다.
     * <p>대기방을 통과한 유저만 좌석 점유가 가능하다.</p>
     *
     * @param concertId 콘서트 ID
     * @param seatId    점유할 좌석 ID
     * @param userId    요청 유저 ID
     * @param token     입장 토큰
     * @return          점유 결과
     */
    SeatLockResponse acquireWithValidation(Long concertId, Long seatId,
                                           Long userId, String token);
}
