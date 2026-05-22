package com.yoo.redis_project.domain.waiting.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EnqueueResult {

    /** 클라이언트 응답 DTO */
    private final WaitingResponse response;

    /** 신규 등록 여부 (true: 201, false: 200) */
    private final boolean created;

    /**
     * 신규 대기열 등록 결과를 생성한다.
     *
     * @param position 1-based 대기 순번
     * @return         201 응답용 EnqueueResult
     */
    public static EnqueueResult newWaiting(int position) {
        return EnqueueResult.builder()
                .response(WaitingResponse.waiting(position))
                .created(true)
                .build();
    }

    /**
     * 기존 대기자 재요청 결과를 생성한다.
     *
     * @param position 1-based 현재 대기 순번
     * @return         200 응답용 EnqueueResult
     */
    public static EnqueueResult existingWaiting(int position) {
        return EnqueueResult.builder()
                .response(WaitingResponse.waiting(position))
                .created(false)
                .build();
    }

    /**
     * 이미 입장 완료된 유저 결과를 생성한다.
     *
     * @param token 입장 토큰 (만료 시 null)
     * @return      200 응답용 EnqueueResult
     */
    public static EnqueueResult entered(String token) {
        return EnqueueResult.builder()
                .response(WaitingResponse.entered(token))
                .created(false)
                .build();
    }
}
