package com.yoo.redis_project.init;

import com.yoo.redis_project.domain.entity.ConcertEntity;
import com.yoo.redis_project.domain.repository.ConcertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 서버 기동 시 초기 데이터를 주입하는 러너.
 *
 * <p>콘서트 데이터가 없을 때만 샘플 데이터를 삽입한다.
 * 운영 환경에서는 별도 프로파일로 비활성화할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final ConcertRepository concertRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (concertRepository.count() > 0) {
            log.info("[DataInitializer] 콘서트 데이터 이미 존재. 초기화 생략.");
            return;
        }

        List<ConcertEntity> concerts = List.of(
                ConcertEntity.builder()
                        .title("2026 IU 콘서트")
                        .artist("IU")
                        .venue("잠실 올림픽 경기장")
                        .startAt(LocalDateTime.of(2026, 8, 1, 19, 0))
                        .bookingOpenAt(LocalDateTime.of(2026, 6, 1, 10, 0))
                        .build()
        );

        concertRepository.saveAll(concerts);
        log.info("[DataInitializer] 콘서트 초기 데이터 {}건 삽입 완료.", concerts.size());
    }
}
