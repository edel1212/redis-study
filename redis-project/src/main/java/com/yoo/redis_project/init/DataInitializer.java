package com.yoo.redis_project.init;

import com.yoo.redis_project.domain.concert.entity.ConcertEntity;
import com.yoo.redis_project.domain.seat.entity.SeatEntity;
import com.yoo.redis_project.domain.concert.repository.ConcertRepository;
import com.yoo.redis_project.domain.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final SeatRepository seatRepository;

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
                        .build(),

                ConcertEntity.builder()
                        .title("2026 흑곰 콘서트")
                        .artist("흑곰")
                        .venue("세종 올림픽 경기장")
                        .startAt(LocalDateTime.of(2026, 6, 1, 10, 10))
                        .bookingOpenAt(LocalDateTime.of(2026, 10, 1, 11, 30)) // 기존 데이터 유지
                        .build(),

                // --- 여기서부터 추가된 데이터 ---

                ConcertEntity.builder()
                        .title("2026 DAY6 연말 단독 콘서트")
                        .artist("DAY6")
                        .venue("KSPO DOME")
                        .startAt(LocalDateTime.of(2026, 12, 24, 19, 30))
                        .bookingOpenAt(LocalDateTime.of(2026, 11, 20, 20, 0))
                        .build(),

                ConcertEntity.builder()
                        .title("2026 성시경의 축가")
                        .artist("성시경")
                        .venue("연세대학교 노천극장")
                        .startAt(LocalDateTime.of(2026, 5, 20, 19, 0))
                        .bookingOpenAt(LocalDateTime.of(2026, 4, 15, 20, 0))
                        .build(),

                ConcertEntity.builder()
                        .title("2026 aespa 2nd World Tour in Seoul")
                        .artist("aespa")
                        .venue("고척 스카이돔")
                        .startAt(LocalDateTime.of(2026, 9, 15, 18, 0))
                        .bookingOpenAt(LocalDateTime.of(2026, 8, 10, 20, 0))
                        .build()
        );

        concertRepository.saveAll(concerts);
        log.info("[DataInitializer] 콘서트 초기 데이터 {}건 삽입 완료.", concerts.size());

        /// /////////////


        List<SeatEntity> seats = new ArrayList<>();
        for(ConcertEntity concert : concerts){
            // 좌석 3개 생성
            seats = List.of(
                    SeatEntity.builder().concert(concert).seatNumber("A-1").build(),
                    SeatEntity.builder().concert(concert).seatNumber("A-2").build(),
                    SeatEntity.builder().concert(concert).seatNumber("A-3").build()
            );

            seatRepository.saveAll(seats);
        }// for

        log.info("[DataInitializer] 콘서트 {}건 + 좌석 {}건 삽입 완료.", concerts.size(), seats.size());
    }
}
