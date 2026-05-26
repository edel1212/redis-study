package com.yoo.redis_project.domain.seat.repository;

import com.yoo.redis_project.domain.seat.entity.SeatEntity;
import com.yoo.redis_project.domain.enums.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<SeatEntity, Long> {
    /**
     * 특정 콘서트의 전체 좌석 목록을 조회한다.
     * <p>좌석맵 화면 렌더링에 사용되며, 4-2에서 캐시 적용 예정.
     *
     * @param concertId 콘서트 ID
     * @return 좌석 목록 (없으면 빈 리스트). 정렬 보장 없음.
     */
    List<SeatEntity> findByConcertId(Long concertId);

    /**
     * 특정 콘서트에서 지정한 상태의 좌석을 조회한다.
     *
     * @param concertId 콘서트 ID
     * @param status   조회할 좌석 상태 (AVAILABLE / HELD / SOLD)
     * @return 해당 상태의 좌석 목록 (없으면 빈 리스트)
     */
    List<SeatEntity> findByConcertIdAndStatus(Long concertId, SeatStatus status);


    /**
     * 특정 콘서트의 상태별 좌석 수를 집계한다.
     * <p>Redis 카운터(INCR/DECR)와 비교/검증용으로 사용.
     *
     * @param concertId 콘서트 ID
     * @param status   집계 대상 상태
     * @return 좌석 수 (없으면 0)
     */
    long countByConcertIdAndStatus(Long concertId, SeatStatus status);
}
