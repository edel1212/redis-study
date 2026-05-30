package com.yoo.redis_project.domain.seat.dto;

import com.yoo.redis_project.domain.seat.entity.SeatEntity;
import com.yoo.redis_project.enums.SeatStatus;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@ToString
public class SeatDto {
    private Long id;
    private String seatNumber;
    private SeatStatus status;

    public static SeatDto from(SeatEntity entity) {
        return SeatDto.builder()
                .id(entity.getId())
                .seatNumber(entity.getSeatNumber())
                .status(entity.getStatus())
                .build();
    }
}
