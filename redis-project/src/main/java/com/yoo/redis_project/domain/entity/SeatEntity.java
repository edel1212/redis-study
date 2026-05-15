package com.yoo.redis_project.domain.entity;

import com.yoo.redis_project.common.entity.BaseTimeEntity;
import com.yoo.redis_project.domain.enums.SeatStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Comment("좌성 정보")
@Table(
        name = "seat",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_seat_concert_number",
                columnNames = {"concert_id", "seat_number"}
        )
)
public class SeatEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("좌석ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concert_id", nullable = false)
    @Comment("콘서트ID")
    private ConcertEntity concert;

    @Column(nullable = false)
    @Comment("좌석번호")
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Comment("좌석 상태")
    private SeatStatus status;

    @Builder
    private SeatEntity(ConcertEntity concert, String seatNumber) {
        this.concert = concert;
        this.seatNumber = seatNumber;
        this.status = SeatStatus.AVAILABLE;
    }

    public void markSold() { this.status = SeatStatus.SOLD; }
    public void markHeld() { this.status = SeatStatus.HELD; }
    public void release()  { this.status = SeatStatus.AVAILABLE; }
}
