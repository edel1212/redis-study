package com.yoo.redis_project.domain.concert.service;

import com.yoo.redis_project.domain.concert.dto.ConcertDto;
import com.yoo.redis_project.domain.seat.dto.SeatDto;

import java.util.List;

/**
 * 콘서트 조회 서비스.
 *
 * <p>Cache Aside 패턴으로 Redis → DB 순으로 조회한다.
 * 캐시 미스 시 DB에서 조회 후 캐시에 적재한다.
 */
public interface ConcertQueryService {
    /**
     * 콘서트 상세를 조회한다.
     *
     * <p>Redis 캐시를 우선 조회하고, 미스 시 DB에서 조회 후 캐시에 적재한다.
     * Redis 장애 시에도 DB 폴백으로 정상 응답한다 (Fail-Open).
     *
     * @param concertId 조회할 콘서트 ID
     * @return 콘서트 DTO
     */
    ConcertDto getConcert(Long concertId);

    /**
     * 콘서트의 전체 좌석 목록을 조회한다.
     *
     * <p>좌석 상태(AVAILABLE/HELD/SOLD)는 실시간성이 중요하므로 TTL을 짧게 유지한다.
     * 빈 좌석 목록은 캐시하지 않는다 (잘못된 concertId 방어).
     *
     * @param concertId 조회할 콘서트 ID
     * @return 좌석 DTO 목록. 존재하지 않으면 빈 리스트
     */
    List<SeatDto> getSeats(Long concertId);

    /**
     * 콘서트 캐시를 무효화한다.
     *
     * <p>콘서트 정보 수정 후 호출한다 (Cache Aside 쓰기 패턴).
     * 상세 캐시와 좌석 목록 캐시를 함께 삭제한다.
     *
     * @param concertId 무효화할 콘서트 ID
     */
    void evictConcertCache(Long concertId);
}
