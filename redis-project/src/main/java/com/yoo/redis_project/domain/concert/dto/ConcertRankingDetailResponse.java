package com.yoo.redis_project.domain.concert.dto;

import lombok.*;

@Getter
@ToString
@Builder(access = AccessLevel.PRIVATE)
public class ConcertRankingDetailResponse {
    private final long        rank;
    private final double     score;
    private final ConcertDto concert;

    /**
     * 랭킹 정보와 콘서트 상세를 병합하여 응답 DTO를 생성한다.
     *
     * @param entry   랭킹 단건 (rank, score, concertId)
     * @param concert 콘서트 상세 정보
     * @return        병합된 랭킹 상세 DTO
     */
    public static ConcertRankingDetailResponse of(
            ConcertRankingEntryResponse entry, ConcertDto concert) {

        return ConcertRankingDetailResponse.builder()
                .rank(entry.getRank())
                .score(entry.getScore())
                .concert(concert)
                .build();
    }
}
