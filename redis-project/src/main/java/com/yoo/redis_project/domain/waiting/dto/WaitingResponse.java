package com.yoo.redis_project.domain.waiting.dto;

import com.yoo.redis_project.enums.WaitingStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class WaitingResponse {
    // 현재 사용자의 상태
    private final WaitingStatus status;
    // 내 위치
    private final Integer       position;   // WAITING일 때만 값 있음
    // 검증 토큰 값
    private final String        token;      // ENTERED일 때만 값 있음

    /**
     * 대기 상태일 경우 응답 반환
     * 
     * @param position 현재 위치
     * @return 대기 상태
     */
    public static WaitingResponse waiting(int position) {
        return WaitingResponse.builder()
                .status(WaitingStatus.WAITING)
                .position(position)
                .build();
    }

    /**
     * 입장이 가능한 상태 응답 반환
     * 
     * @param token 입장 가능 시 전달 받는 토큰
     * @return 현재 입장 가능 상태 응답
     */
    public static WaitingResponse entered(String token) {
        return WaitingResponse.builder()
                .status(WaitingStatus.ENTERED)
                .token(token)
                .build();
    }

    /**
     * 대기열에 입장을 하지 않은 사용자
     * 
     * @return 아직 입장 대기열에 등록되지 않은 상태 응답
     */
    public static WaitingResponse notInQueue() {
        return WaitingResponse.builder()
                .status(WaitingStatus.NOT_IN_QUEUE)
                .build();
    }

    /**
     * token 시간 만료로 인한 응답
     *
     * @return token 시간 만료
     */
    public static WaitingResponse expired() {
        return WaitingResponse.builder()
                .status(WaitingStatus.NOT_IN_QUEUE)
                .build();
    }
}
