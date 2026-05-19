package com.yoo.redis_project.domain.service;

import com.yoo.redis_project.common.constants.RedisKeyConstants;
import com.yoo.redis_project.domain.dto.ConcertRankingEntryResponse;
import com.yoo.redis_project.domain.repository.ConcertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class ConcertRankingServiceImpl implements ConcertRankingService{

    private final StringRedisTemplate redisTemplate;
    private final ConcertRepository concertRepository;

    @Override
    public void incrementScore(Long concertId) {
        String key = RedisKeyConstants.CONCERT_RANKING;
        redisTemplate.opsForZSet().incrementScore(
                                                    // key
                                                    key,
                                                    // value
                                                    String.valueOf(concertId),
                                                    // score
                                                    1.0
                                            );
    }

    @Override
    public List<ConcertRankingEntryResponse> getTopRanking(int size) {
        String key = RedisKeyConstants.CONCERT_RANKING;
        // 기존 키 기준으로 내림 차순 정렬 범위 : 0 ~ size 까지
        Set<ZSetOperations.TypedTuple<String>> tuples =  redisTemplate.opsForZSet().reverseRangeWithScores(
                                                        // key
                                                        key
                                                        // start
                                                        ,0
                                                        // end
                                                        , size - 1
                                                );

        // Reids에서 값이 없을 경우 빈 배열 반환
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }//if

        List<ConcertRankingEntryResponse> result = new ArrayList<>();
        long rank = 1;

        // [Key] 개수만큼 loop
        for(ZSetOperations.TypedTuple<String> tuple : tuples){
            // ZSetOperations.TypedTuple<String> 는 Value:Score 형태로 저정되어 있음
            result.add(ConcertRankingEntryResponse.from(tuple, rank++));
        } // for

        return result;
    }

    @Override
    public Optional<Integer> getRank(Long concertId) {
        String key = RedisKeyConstants.CONCERT_RANKING;

        // reverseRank 를 통해 key:value 값을 넣어 슨위를 구함
        Long rank = redisTemplate.opsForZSet().reverseRank(key, concertId);

        return (rank == null)
                ? Optional.empty()
                // 0부터 시작하기에 +1로 진행
                : Optional.of(rank.intValue() + 1);
    }


    /**
     * 랭킹 키에 해당 콘서트가 없으면 DB views 값으로 초기화한다.
     * <p>Redis 최초 기동 또는 키 만료 후 재진입 시 score 괴리를 방지한다.</p>
     *
     * @param concertId 초기화 대상 콘서트 ID
     */
    private void initRankingScoreIfAbsent(Long concertId) {
        String key = RedisKeyConstants.CONCERT_RANKING;
        // key와 value를 통해 score를 가져옴
        Double existing = redisTemplate.opsForZSet().score(key, String.valueOf(concertId));

        if (existing == null) {
            concertRepository.findById(concertId)
                    // 해당 콘서트가 존재할 경우
                    .ifPresent(concert ->
                        // Ranking Key 등록
                        redisTemplate.opsForZSet().add(key, String.valueOf(concertId), concert.getViews())
            );
        }
    }
}
