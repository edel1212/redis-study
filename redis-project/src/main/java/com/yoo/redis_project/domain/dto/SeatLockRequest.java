package com.yoo.redis_project.domain.dto;

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
    private Long userId;
    private Long concertId;
}
