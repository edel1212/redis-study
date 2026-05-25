package com.yoo.redis_project.domain.booking.service;

import com.yoo.redis_project.domain.booking.dto.BookingResponse;

public interface BookingService {
    /**
     * 점유된 좌석의 예매를 확정한다.
     * <p>좌석 락 소유자 검증 → SeatStatus.SOLD 변경
     * → 입장 자리 반환(entered/token 제거) → 락 해제를 수행한다.</p>
     *
     * @param seatId 예매 확정할 좌석 ID
     * @param userId 요청 유저 ID
     * @return       예매 확정 결과
     */
    BookingResponse confirm(Long seatId, Long userId);
}
