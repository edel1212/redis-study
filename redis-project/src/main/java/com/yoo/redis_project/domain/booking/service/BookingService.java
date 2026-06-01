package com.yoo.redis_project.domain.booking.service;

public interface BookingService {

    /**
     * 점유 중인 좌석을 SOLD로 확정하고 소속 콘서트 ID를 반환한다.
     * <p>이 메서드는 DB 트랜잭션만 담당하며, 정상 리턴 시 SOLD가 커밋된다.
     * Redis 락 소유자 검증/정리는 호출 측(Facade)이 책임진다.
     *
     * @param seatId 확정 대상 좌석 ID
     * @return 좌석이 속한 콘서트 ID (커밋 후 대기열 정리에 사용)
     * @throws com.yoo.redis_project.exception.custom.ResourceNotFoundException 좌석이 존재하지 않을 때
     *         (빈 결과를 null/Optional로 흘리지 않고 예외로 처리)
     */
    Long markAsSold(Long seatId);
}
