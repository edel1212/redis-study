package com.yoo.redis_project.domain.booking.service;

import com.yoo.redis_project.domain.booking.dto.BookingResponse;

public interface BookingFacadeService {
    /**
     * 좌석 점유 소유자를 검증하고 예매를 최종 확정한다.
     * <p>Redis 소유자 검증 → DB SOLD 커밋 → (커밋 후) 대기열/락 정리 순으로 동작한다.
     * </br>
     * Redis 정리는 DB 커밋이 끝난 뒤 실행되며, <b>정리 실패는 TTL·스케줄러로
     * 자가 치유되어 확정 결과(SOLD)에 영향을 주지 않는다.</b>
     *
     * @param seatId 확정 대상 좌석 ID (Redis 락 소유자와 userId가 일치해야 함)
     * @param userId 예매 요청 사용자 ID
     * @return 확정 결과. 미점유/소유자 불일치 시 {@link com.yoo.redis_project.domain.booking.dto.BookingResponse#fail}
     *         (성공 아님 — 호출 측 분기 필요), 성공 시 {@link com.yoo.redis_project.domain.booking.dto.BookingResponse#success}
     */
    BookingResponse confirm(Long seatId, Long userId);
}
