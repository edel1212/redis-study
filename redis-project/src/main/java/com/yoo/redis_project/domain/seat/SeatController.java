package com.yoo.redis_project.domain.seat;

import com.yoo.redis_project.domain.seat.dto.SeatLockRequest;
import com.yoo.redis_project.domain.seat.dto.SeatLockResponse;
import com.yoo.redis_project.domain.seat.service.SeatFacadeService;
import com.yoo.redis_project.domain.seat.service.SeatLockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/seats")
public class SeatController {
    private final SeatLockService seatLockService;
    private final SeatFacadeService seatFacadeService;

    /**
     * 좌석 임시 점유 획득.
     * 동시 요청 시 단 한 명만 성공한다.
     * <p>실제 운영 환경일 경우 Security Context에서 사용자 정보를 추출하여 사용</p>
     */
    @PostMapping("/{seatId}/lock")
    public ResponseEntity<SeatLockResponse> acquire(
            @RequestHeader("X-Entry-Token") @NotBlank(message = "token은 공백일 수 없습니다.") String token,
            @PathVariable Long seatId,
            @Valid @RequestBody SeatLockRequest request
    ) {
        SeatLockResponse response = seatFacadeService
                .acquireWithValidation(request.getConcertId(), seatId, request.getUserId(), token);

        if (!response.isAcquired()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } // if

        return ResponseEntity.ok(response);
    }

    /**
     * 좌석 임시 점유 해제.
     * 본인 락만 해제 가능하다.
     *
     * <p>실제 운영 환경일 경우 Security Context에서 사용자 정보를 추출하여 사용</p>
     *
     */
    @DeleteMapping("/{seatId}/lock")
    public ResponseEntity<SeatLockResponse> release(
            @PathVariable Long seatId,
            @RequestBody SeatLockRequest request
    ) {
        Long userId = request.getUserId();

        // 점유 해제
        boolean released = seatLockService.release(seatId, userId);

        if (released) {
            return ResponseEntity.ok(SeatLockResponse.success(seatId, userId));
        } // if
        
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(SeatLockResponse.fail(seatId, userId));
    }

}
