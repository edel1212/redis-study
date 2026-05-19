package com.yoo.redis_project.domain.service;

import com.yoo.redis_project.common.constants.RedisKeyConstants;
import com.yoo.redis_project.domain.entity.ConcertEntity;
import com.yoo.redis_project.domain.repository.ConcertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConcertViewServiceImpl implements ConcertViewService{

    private static final Duration DELTA_TTL = Duration.ofHours(1);

    // Redis
    private final StringRedisTemplate stringRedisTemplate;
    // concert
    private final ConcertRepository concertRepository;
    // ranking
    private final ConcertRankingService rankingService;


    @Override
    public void increment(Long concertId) {
        String key = RedisKeyConstants.CONCERT_VIEW_DELTA.formatted(concertId);

        try {
            // 해당 key  값 증가
            stringRedisTemplate.opsForValue().increment(key);
            // 만료 시간 갱신
            stringRedisTemplate.expire(key, DELTA_TTL);

            // 랭킹 값 증가
            rankingService.incrementScore(concertId);
            
        } catch (DataAccessException e){
            // 조회수 누락은 허용 — 서비스 중단 없이 Fail-Open
            log.warn("[ConcertView] 조회수 증가 실패. concertId={}", concertId, e);
        } // try - catch

    }

    // ----------------------------------------------------------------
    // 배치 동기화
    // ----------------------------------------------------------------

    @Transactional
    @Override
    public void syncViewsToDB() {
        List<ConcertEntity> concerts = concertRepository.findAll();

        // 전체 concert loop
        for(ConcertEntity concert : concerts){
            String key = RedisKeyConstants.CONCERT_VIEW_DELTA.formatted(concert.getId());

            try {
                // getAndSet: delta 읽기 + 0으로 리셋 (원자적)
                String previous = stringRedisTemplate.opsForValue().getAndSet(key, "0");

                // Redis에 저장된 값이 없거나 0일 경우 skips
                if (previous == null || previous.equals("0")) {
                    continue; // 증분 없음 — DB UPDATE 생략
                }// if

                // [db update] 조회수에 캐싱 값을 더해줌
                long delta = Long.parseLong(previous);
                concert.addViews(delta);

                log.info("[ConcertView] 조회수 동기화. concertId={} delta={} result={}", concert.getId(), delta, concert.getVenue());

            } catch (DataAccessException e) {
                log.warn("[ConcertView] Redis 통신 장애. concertId={} 동기화 생략",
                        concert.getId(), e);
            } catch (NumberFormatException e) {
                log.warn("[ConcertView] delta 파싱 실패. concertId={} 키 초기화",
                        concert.getId(), e);
                stringRedisTemplate.delete(key); // 깨진 키 제거
            } // try - catch

        } // for

    }
}
