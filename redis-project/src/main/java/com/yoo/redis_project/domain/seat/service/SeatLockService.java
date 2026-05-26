package com.yoo.redis_project.domain.seat.service;

import java.util.Optional;

/**
 * 좌석 임시 점유 락 서비스.
 *
 * <p>Redis SET NX EX 기반의 단순 락으로 좌석 중복 점유를 방지한다.
 * 결제 확정 구간의 강한 락(Redisson)과 달리,
 * 이 락은 사용자가 좌석을 선택한 후 결제 완료 전까지의 임시 점유에 사용한다.
 */
public interface SeatLockService {

    /**
     * 좌석 임시 점유 락을 획득한다.
     *
     * <p>SET NX EX 로 원자적으로 실행된다.
     * 이미 다른 사용자가 점유 중이면 획득 실패한다.
     *
     * @param seatId 점유할 좌석 ID
     * @param userId 점유 요청 유저 ID (락 소유자 식별용)
     * @return 획득 성공 시 {@code true}, 이미 점유 중이면 {@code false}
     */
    boolean acquire(Long seatId, Long userId);

    /**
     * 좌석 임시 점유 락을 해제한다.
     *
     * <p>락 소유자 확인 후 DEL 한다.
     * 본인 락이 아니면 해제하지 않는다.
     *
     * @param seatId 해제할 좌석 ID
     * @param userId 해제 요청 유저 ID
     * @return 해제 성공 시 {@code true}, 소유자 불일치 또는 만료 시 {@code false}
     */
    boolean release(Long seatId, Long userId);

    /**
     * 좌석 임시 점유 락 소유자를 조회한다.
     *
     * <p>락이 없으면 {@link Optional#empty()} 를 반환한다.
     *
     * @param seatId 조회할 좌석 ID
     * @return 락 소유자 userId, 락 없으면 {@link Optional#empty()}
     */
    Optional<Long> getLockOwner(Long seatId);
}
