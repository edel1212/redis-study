package com.yoo.redis_project.domain.seat.entity;

import com.yoo.redis_project.common.entity.BaseTimeEntity;
import com.yoo.redis_project.domain.concert.entity.ConcertEntity;
import com.yoo.redis_project.enums.SeatStatus;
import com.yoo.redis_project.exception.custom.BadRequestException;
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

    /**
     * 좌석이 점유 가능한 상태인지 검증한다.
     *
     * @throws BadRequestException 이미 판매되었거나 점유된 좌석인 경우
     */
    public void validateAvailable() {
        if (this.status == SeatStatus.SOLD) {
            throw new BadRequestException("이미 판매된 좌석입니다.");
        }
        if (this.status == SeatStatus.HELD) {
            throw new BadRequestException("이미 점유된 좌석입니다.");
        }
    }

    /**
     * 좌석 점유 처리
     */
    public void markHeld() {
        validateAvailable();
        this.status = SeatStatus.HELD;
    }

    public void release()  { this.status = SeatStatus.AVAILABLE; }

    /**
     * 좌석 상태를 SOLD로 변경한다.
     *
     * @throws IllegalStateException 이미 판매된 좌석인 경우
     */
    public void markAsSold() {
        if(this.status != SeatStatus.HELD){
            throw new BadRequestException("점유된 상태의 좌석이 아닙니다.");
        } // if
        this.status = SeatStatus.SOLD;
    }
}
