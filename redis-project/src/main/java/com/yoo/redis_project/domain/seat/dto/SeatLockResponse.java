package com.yoo.redis_project.domain.seat.dto;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
public class SeatLockResponse {
    private Long seatId;
    private Long userId;
    // 점유 여부
    private boolean acquired;
    private String message;

    public static SeatLockResponse success(Long seatId, Long userId) {
        return new SeatLockResponse(seatId, userId, true, "좌석 임시 점유 성공");
    }

    public static SeatLockResponse fail(Long seatId, Long userId) {
        return new SeatLockResponse(seatId, userId, false, "이미 점유된 좌석입니다");
    }
}
