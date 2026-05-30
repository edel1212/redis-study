package com.yoo.redis_project.domain.concert.service;

import com.yoo.redis_project.domain.concert.dto.ConcertRankingDetailResponse;
import com.yoo.redis_project.domain.concert.dto.ConcertRankingEntryResponse;

import java.util.List;
import java.util.Optional;

public interface ConcertRankingService {

    /**
     * 콘서트 조회 시 랭킹 점수를 1 증가시킨다.
     * <p>INCR(delta)와 함께 호출되며, 별도 TTL 없이 누적된다.</p>
     *
     * @param concertId 점수를 증가시킬 콘서트 ID
     */
    void incrementScore(Long concertId);

    /**
     * 조회수 기준 상위 N개 콘서트 랭킹을 반환한다.
     * <p>score 내림차순 정렬이며, score는 Redis 기준 누적 조회수다.</p>
     *
     * @param size    조회할 상위 랭킹 수 (1 이상)
     * @return        순위 포함 랭킹 목록. 데이터 없으면 빈 리스트 반환
     */
    List<ConcertRankingEntryResponse> getTopRanking(int size);


    /**
     * 조회수 기준 상위 N개 콘서트 랭킹 + 상세 정보를 반환한다.
     * <p>MGET으로 캐시된 콘서트 상세를 일괄 조회하며,
     * cache miss 항목은 DB에서 개별 조회 후 캐시에 저장한다.</p>
     *
     * @param size 조회할 상위 랭킹 수 (1 이상)
     * @return     랭킹 + 상세 병합 목록. 데이터 없으면 빈 리스트 반환
     */
    List<ConcertRankingDetailResponse> getTopRankingWithDetail(int size);

    /**
     * 특정 콘서트의 현재 랭킹 순위를 반환한다.
     * <p>순위는 1-based이며, 랭킹에 없으면 Optional.empty()를 반환한다.</p>
     *
     * @param concertId 순위를 조회할 콘서트 ID
     * @return          1-based 순위. 랭킹 미등록 시 Optional.empty()
     */
    Optional<Integer> getRank(Long concertId);

}
