package com.yoo.redis_project.domain.waiting.service;

import com.yoo.redis_project.domain.waiting.dto.EnqueueResult;
import com.yoo.redis_project.domain.waiting.dto.WaitingResponse;

public interface WaitingService {

    /**
     * 콘서트 대기열에 진입한다. (멱등)
     * <p>이미 입장 완료된 유저는 토큰을 반환하고,
     * 이미 대기 중인 유저는 현재 순번을 반환한다.</p>
     *
     * @param concertId 대기열 대상 콘서트 ID
     * @param userId    진입 유저 ID
     * @return          현재 상태 (NOT_IN_QUEUE / WAITING / ENTERED)
     */
    EnqueueResult enqueue(Long concertId, Long userId);

    /**
     * 대기열 내 현재 순번을 조회한다. (폴링용)
     * <p>프론트가 3~5초 간격으로 반복 호출하며,
     * 입장 완료 시 토큰을 함께 반환한다.</p>
     *
     * @param concertId 대기열 대상 콘서트 ID
     * @param userId    조회 유저 ID
     * @return          현재 상태 (WAITING / ENTERED / NOT_IN_QUEUE)
     */
    WaitingResponse getPosition(Long concertId, Long userId);

    /**
     * 대기열에서 상위 유저를 입장시킨다. (스케줄러 전용)
     * <p>만료자 정리 후 빈 자리만큼 ZPOPMIN으로 꺼내
     * 토큰 발급 + entered 등록을 수행한다.</p>
     *
     * @param concertId 대기열 대상 콘서트 ID
     */
    void processEntry(Long concertId);

    /**
     * 가상 공간 입장 자리를 반환한다.
     * <p>좌석 최종 예약 성공 시 호출</p>
     *
     * @param concertId 콘서트 ID
     * @param userId    유저 ID
     */
    void releaseEntry(Long concertId, Long userId);
}
