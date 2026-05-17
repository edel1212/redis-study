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
     *
     * @param concertId 조회수를 증가시킬 콘서트 ID
     */
    void increment(Long concertId);

    /**
     * 모든 콘서트의 조회수 delta를 DB에 동기화한다.
     *
     * <p>스케줄러에서 주기적으로 호출한다.
     * delta를 원자적으로 0으로 초기화하고 DB에 반영한다.
     */
    void syncViewsToDB();
}
