package com.yoo.redis_project.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoo.redis_project.common.constants.RedisKeyConstants;
import com.yoo.redis_project.domain.dto.ConcertDto;
import com.yoo.redis_project.domain.dto.SeatDto;
import com.yoo.redis_project.domain.repository.ConcertRepository;
import com.yoo.redis_project.domain.repository.SeatRepository;
import com.yoo.redis_project.exception.cusom.ResourceNotFoundException;
import com.yoo.redis_project.utils.RedisCacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConcertQueryServiceImpl implements ConcertQueryService {
    private final ConcertRepository concertRepository;
    private final SeatRepository seatRepository;
    private final RedisCacheHelper cacheHelper;
    private final ObjectMapper objectMapper;

    // TTL 상수
    // 콘서트 정보
    private static final Duration CONCERT_TTL = Duration.ofMinutes(30);
    // 좌성 정보
    private static final Duration SEATS_TTL   = Duration.ofSeconds(30);

    @Override
    @Transactional(readOnly = true)
    public ConcertDto getConcert(Long concertId) {
        // Reids에 저장된 콘서트 Key
        String key = RedisKeyConstants.CONCERT_DETAIL.formatted(concertId);

        // Redis 조회
        Optional<ConcertDto> cached = cacheHelper.get(key, ConcertDto.class);

        // cache Hit
        if(cached.isPresent()){
            log.debug("[ConcertQuery] Get Detail 캐시 Hit. key={}", key);
            return cached.get();
        }// if

        log.debug("[ConcertQuery] Get Detail 캐시 Miss. DB read concertId={}", key);

        // DB 조회
        Optional<ConcertDto> dto = concertRepository.findById(concertId)
                .map(ConcertDto::from);

        // 캐시 적재 (존재하는 경우만)
        dto.ifPresent(concert -> cacheHelper.set(key, concert, CONCERT_TTL));

        return dto.orElseThrow(() ->
                new ResourceNotFoundException("콘서트 없음. id=" + concertId));
    }

    @Transactional(readOnly = true)
    @Override
    public List<SeatDto> getSeats(Long concertId) {
        // Reids에 저장된 좌석 Key
        String key = RedisKeyConstants.CONCERT_SEATS.formatted(concertId);

        Optional<List<SeatDto>> cached = cacheHelper.getList(key, SeatDto.class);
        // cache Hit
        if(cached.isPresent()){
            log.debug("[SeatQuery] Get Detail 캐시 Hit. key={}", key);
            return cached.get();
        }// if

        log.debug("[SeatQuery] Get Detail 캐시 Miss. DB read concertId={}", key);

        // DB 조회
        List<SeatDto> seats = seatRepository.findByConcertId(concertId)
                .stream()
                .map(SeatDto::from)
                .toList();

        // cache update
        if (!seats.isEmpty()) {
            cacheHelper.set(key, seats, SEATS_TTL);
        } // if

        return seats;
    }

    @Override
    public void evictConcertCache(Long concertId) {
        cacheHelper.delete(RedisKeyConstants.CONCERT_DETAIL.formatted(concertId));
        cacheHelper.delete(RedisKeyConstants.CONCERT_SEATS.formatted(concertId));
        log.info("[ConcertQuery] 캐시 무효화 완료. concertId={}", concertId);
    }
}
