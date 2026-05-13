package com.yoo.redis_project.domain.entity;

import com.yoo.redis_project.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Comment("콘서트 정보")
@Table(name = "concert")
public class ConcertEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("콘서트ID")
    private Long id;

    @Column(nullable = false, length = 200)
    @Comment("제목")
    private String title;

    @Column(nullable = false, length = 100)
    @Comment("아티스트")
    private String artist;

    @Column(nullable = false, length = 100)
    @Comment("장소")
    private String venue;

    @Column(nullable = false)
    @Comment("시작 시간")
    private LocalDateTime startAt;

    @Column(nullable = false)
    @Comment("예매 오픈 시각")
    private LocalDateTime bookingOpenAt;

    @Builder
    private ConcertEntity(String title, String artist, String venue,
                    LocalDateTime startAt, LocalDateTime bookingOpenAt) {
        this.title = title;
        this.artist = artist;
        this.venue = venue;
        this.startAt = startAt;
        this.bookingOpenAt = bookingOpenAt;
    }
}
