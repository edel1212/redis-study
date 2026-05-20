package com.yoo.redis_project.domain.service;

/**
 * 콘서트 조회수 카운터 서비스.
 *
 * <p>Redis INCR 기반으로 조회수를 누적하고,
 * 배치 스케줄러가 주기적으로 DB에 동기화한다.
 */
public interface ConcertViewService {

    /**
     * 콘서트 조회수를 1 증가시킨다.
     *
     * <p>Redis delta 키에 INCR 후 TTL을 연장한다.
     * Redis 장애 시 조회수 누락은 허용한다 (Fail-Open).
     * </p>
     * <p>같은 유저가 같은 날 여러 번 조회해도 1회만 카운트된다.</p>
     *
     * @param userId    조회한 유저 ID (어뷰징 방지용)
     * @param concertId 조회수를 증가시킬 콘서트 ID
     */
    void increment(Long concertId, Long userId);

    /**
     * 모든 콘서트의 조회수 delta를 DB에 동기화한다.
     *
     * <p>스케줄러에서 주기적으로 호출한다.
     * delta를 원자적으로 0으로 초기화하고 DB에 반영한다.
     */
    void syncViewsToDB();
}
