package com.yoo.redis_project.scheduler;

import com.yoo.redis_project.domain.concert.repository.ConcertRepository;
import com.yoo.redis_project.domain.waiting.service.WaitingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 스케줄링을 통한 사용자 대기열에 등록되어 있는 사용자 입장 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingEntryScheduler {
    private final WaitingService waitingService;
    private final ConcertRepository concertRepository;

    /**
     * 3초 간격으로 대기열 입장 처리를 실행한다.
     * <p>활성 콘서트별로 만료자 정리 + 빈 자리 입장을 수행한다.</p>
     */
    @Scheduled(fixedDelay = 3000)
    public void processAllQueues() {
        // 학습용: 전체 콘서트에 대해 실행
        // 실무: 활성 대기방이 있는 콘서트만 필터링
        concertRepository.findAll().forEach(concert ->
                waitingService.processEntry(concert.getId())
        );
    }
}
