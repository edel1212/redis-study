package com.yoo.redis_project.domain.booking;

import com.yoo.redis_project.domain.booking.dto.BookingResponse;
import com.yoo.redis_project.domain.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bookings")
public class BookingController {

    private final BookingService bookingService;

    /**
     * POST /bookings/seats/{seatId}?userId=1
     * 점유된 좌석의 예매를 확정한다.
     */
    @PostMapping("/seats/{seatId}")
    public ResponseEntity<BookingResponse> confirm(@PathVariable Long seatId,
                                                        @RequestParam Long userId) {

        BookingResponse response = bookingService.confirm(seatId, userId);

        // 예매 실패시 반환
        if (!response.isConfirmed()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } // if

        return ResponseEntity.ok(response);
    }
}
