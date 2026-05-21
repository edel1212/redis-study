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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConcertViewServiceImpl implements ConcertViewService{

    private static final Duration DELTA_TTL = Duration.ofHours(1);
    private static final Duration VIEWERS_TTL    = Duration.ofDays(2);

    // Redis
    private final StringRedisTemplate stringRedisTemplate;
    // concert
    private final ConcertRepository concertRepository;
    // ranking
    private final ConcertRankingService rankingService;


    @Override
    public void increment(Long concertId, Long userId) {
        // 조회수 증감 key
        String key = RedisKeyConstants.CONCERT_VIEW_DELTA.formatted(concertId);
        // 날짜별 조회 Set Key
        String viewersKey = RedisKeyConstants.CONCERT_DAILY_VIEWERS.formatted(concertId, LocalDate.now().toString());

        try {

            // 어뷰징 방지 사용자 추가( 저장 시 값을 기준으로 존재 유무 체크 )
            Long added = stringRedisTemplate.opsForSet().add(viewersKey, String.valueOf(userId));
            // 이미 조회한 사용자이기에 skip
            if(added == null || added == 0L){
                log.info("이미 조화한 사용자  count 및 ranking 증감 X");
                return;
            } //if
            // TTL 설정 (다음날 00시 00분까지)
            Date midnightDate = Date.from(LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            stringRedisTemplate.expireAt(viewersKey, midnightDate);

            // 조회 수 key 값 증가
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
                String previous = stringRedisTemplate.opsForValue().get(key);

                // Redis에 저장된 값이 없거나 0일 경우 skips
                if (previous == null || previous.equals("0")) {
                    continue; // 증분 없음 — DB UPDATE 생략
                }// if

                // [db update] 조회수에 캐싱 값을 더해줌
                long delta = Long.parseLong(previous);
                concert.addViews(delta);

                // 💡 기존 getAndSet 방식 ->  DB 업데이트가 성공하면 Redis에서 키를 완전히 삭제 하는 방식으로 변경
                // Cache Aside 방식 채택
                stringRedisTemplate.delete(key);

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
