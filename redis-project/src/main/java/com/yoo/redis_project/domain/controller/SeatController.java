package com.yoo.redis_project.domain.controller;

import com.yoo.redis_project.domain.dto.SeatLockRequest;
import com.yoo.redis_project.domain.dto.SeatLockResponse;
import com.yoo.redis_project.domain.service.SeatLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/seats")
public class SeatController {
    private final SeatLockService seatLockService;

    /**
     * 좌석 임시 점유 획득.
     * 동시 요청 시 단 한 명만 성공한다.
     * <p>실제 운영 환경일 경우 Security Context에서 사용자 정보를 추출하여 사용</p>
     */
    @PostMapping("/{seatId}/lock")
    public ResponseEntity<SeatLockResponse> acquire(
            @PathVariable String seatId,
            @RequestBody SeatLockRequest request
    ) {
        Long userId = request.getUserId();
        boolean acquired = seatLockService.acquire(seatId, userId);

        if (acquired) {
            return ResponseEntity.ok(SeatLockResponse.success(seatId, userId));
        }
        // 409 Conflict — 이미 다른 사용자가 점유 중
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(SeatLockResponse.fail(seatId, userId));
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
            @PathVariable String seatId,
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
