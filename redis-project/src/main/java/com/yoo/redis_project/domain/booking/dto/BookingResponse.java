package com.yoo.redis_project.domain.booking.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class BookingResponse {

    private final Long    seatId;
    private final Long    userId;
    private final boolean confirmed;
    private final String  message;

    public static BookingResponse success(Long seatId, Long userId) {
        return BookingResponse.builder()
                .seatId(seatId)
                .userId(userId)
                .confirmed(true)
                .message("예매가 확정되었습니다.")
                .build();
    }

    public static BookingResponse fail(Long seatId, String message) {
        return BookingResponse.builder()
                .seatId(seatId)
                .confirmed(false)
                .message(message)
                .build();
    }
}
