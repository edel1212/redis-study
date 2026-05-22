package com.yoo.redis_project.domain.waiting.service;

import com.yoo.redis_project.domain.waiting.dto.EnqueueResult;

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
}
