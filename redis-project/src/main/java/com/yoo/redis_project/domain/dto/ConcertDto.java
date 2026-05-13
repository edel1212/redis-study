package com.yoo.redis_project.domain.dto;

import com.yoo.redis_project.domain.entity.ConcertEntity;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ConcertDto {

    private Long id;
    private String title;
    private String artist;
    private String venue;
    private LocalDateTime startAt;
    private LocalDateTime bookingOpenAt;

    /** Entity → DTO 변환 */
    public static ConcertDto from(ConcertEntity entity) {
        return ConcertDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .artist(entity.getArtist())
                .venue(entity.getVenue())
                .startAt(entity.getStartAt())
                .bookingOpenAt(entity.getBookingOpenAt())
                .build();
    }
}