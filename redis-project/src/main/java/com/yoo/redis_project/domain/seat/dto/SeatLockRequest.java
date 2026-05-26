package com.yoo.redis_project.domain.seat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * 좌석 임시 점유 요청 DTO.
 *
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
public class SeatLockRequest {
    @NotNull(message = "유저 ID는 필수입니다.")
    private Long userId;

    @NotNull(message = "콘서트 ID는 필수입니다.")
    private Long concertId;
}
