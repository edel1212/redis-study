package com.yoo.redis_project.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Objects;

@Getter
@AllArgsConstructor
public class ConcertRankingEntryResponse {

    private final Long   concertId;
    private final double score;
    private final long    rank;

    /**
     * Sorted Set 조회 결과 한 건을 랭킹 DTO로 변환한다.
     *
     * @param tuple   Redis ZSetOperations.TypedTuple (member + score)
     * @param rank    1-based 순위
     * @return        변환된 ConcertRankingEntry
     */
    public static ConcertRankingEntryResponse from(
            ZSetOperations.TypedTuple<String> tuple, long rank) {

        Long concertId = Long.parseLong(
                Objects.requireNonNull(tuple.getValue()));
        double score   = Objects.requireNonNull(tuple.getScore());

        return new ConcertRankingEntryResponse(concertId, score, rank);
    }
}
