package com.yoo.redis_project.scheduler;

import com.yoo.redis_project.domain.concert.service.ConcertViewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 콘서트 조회수 배치 동기화 스케줄러.
 *
 * <p>Redis delta를 주기적으로 DB에 반영한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConcertViewSyncScheduler {

    private final ConcertViewService concertViewService;

    /**
     * 10분마다 조회수 동기화.
     * fixedDelay: 이전 실행 완료 후 10분 대기 (중복 실행 방지)
     * <p>MSA 구조의 경우 해당 스케줄링 전용 서버로 분리 필요</p>
     */
    @Scheduled(fixedDelay = 600_000)
    public void sync() {
        log.info("[ConcertViewSync] 조회수 동기화 시작");
        concertViewService.syncViewsToDB();
        log.info("[ConcertViewSync] 조회수 동기화 완료");
    }
}
