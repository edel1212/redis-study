package com.yoo.redis_project.domain.concert.entity;

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

    @Column(name = "views", nullable = false)
    @Comment("누적 조회수 (배치 동기화 기준)")
    private long views = 0L;

    /**
     * 배치 동기화 시 delta 값을 view에 반영한다.
     *
     * @param delta 이번 배치 주기의 증분
     */
    public void addViews(long delta) {
        this.views += delta;
    }

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
