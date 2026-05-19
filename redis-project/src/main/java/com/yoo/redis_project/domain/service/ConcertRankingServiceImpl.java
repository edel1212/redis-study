package com.yoo.redis_project.domain.service;

import com.yoo.redis_project.common.constants.RedisKeyConstants;
import com.yoo.redis_project.domain.dto.ConcertDto;
import com.yoo.redis_project.domain.dto.ConcertRankingDetailResponse;
import com.yoo.redis_project.domain.dto.ConcertRankingEntryResponse;
import com.yoo.redis_project.domain.repository.ConcertRepository;
import com.yoo.redis_project.utils.RedisCacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class ConcertRankingServiceImpl implements ConcertRankingService{

    private final StringRedisTemplate redisTemplate;
    private final ConcertRepository concertRepository;
    private final RedisCacheHelper redisCacheHelper;

    private static final Duration CONCERT_TTL = Duration.ofMinutes(30);

    @Override
    public void incrementScore(Long concertId) {
        String key = RedisKeyConstants.CONCERT_RANKING;

        // 값이 없을 경우 DB 조회 후 init (트래픽에 따라 - 스케줄링 or bootRun 방식으로 변경)
        initRankingScoreIfAbsent(concertId);

        // 값 증감
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
    public List<ConcertRankingDetailResponse> getTopRankingWithDetail(int size) {

        // 1. cache 된 랭킹 조회 - 간략한 정보면 캐싱되어 저장되어 있음
        List<ConcertRankingEntryResponse> entries = getTopRanking(size);

        // 랭킹 정보가 없으면 아무것도 없이 반환
        if (entries.isEmpty()) return Collections.emptyList();

        // concertId 추출 후 콘서트 상세정보를 가져올 keys로 변환
        List<String> keys =  entries.stream()
                .map( i -> RedisKeyConstants.CONCERT_DETAIL.formatted(i.getConcertId()) )
                .toList();

        // 조회된 랭킹 key룰 통해 콘서트 상세 정보를 Mget 해 옴
        List<ConcertDto> cached = redisCacheHelper.multiGet(keys, ConcertDto.class);

        // 결과를 담을 변수
        List<ConcertRankingDetailResponse> result = new ArrayList<>();

        for(int i = 0 ; i < entries.size(); i++){
            // 랭킹 축약 정보
            ConcertRankingEntryResponse entry = entries.get(i);
            // 콘서트 상세정보
            ConcertDto concert = cached.get(i);

            // 콘서트 상세정보에 캐싱된 정보가 없다면 새로 저장
            if(concert == null){
                concert = concertDetailLoadAndCache(entry.getConcertId());
            } // if

            // 새로 갱신할 상세 정보가 없다면 skip
            if (concert == null) continue;

            result.add(ConcertRankingDetailResponse.of(entry, concert));

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

    /**
     * 콘서트 상세정보 조회 및 캐싱 저장
     *
     * <p>DB에 저장된 정보가 없을 경우 null 반환</p>
     *
     * @param concertId 콘서트 식별 ID
     * @return 콘서트 상세 정보
     */
    private ConcertDto concertDetailLoadAndCache(Long concertId) {
        return concertRepository.findById(concertId)
                .map(entity -> {
                    // entity -> DTO
                    ConcertDto dto = ConcertDto.from(entity);
                    // Redis 저장
                    redisCacheHelper.set(
                            RedisKeyConstants.CONCERT_DETAIL.formatted(concertId),
                            dto,
                            CONCERT_TTL);
                    return dto;
                })
                .orElse(null);
    }
}
