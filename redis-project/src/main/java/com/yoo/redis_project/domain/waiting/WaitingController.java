package com.yoo.redis_project.domain.waiting;

import com.yoo.redis_project.domain.waiting.dto.EnqueueResult;
import com.yoo.redis_project.domain.waiting.dto.WaitingResponse;
import com.yoo.redis_project.domain.waiting.service.WaitingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/concerts/{concertId}/waiting")
public class WaitingController {

    private final WaitingService waitingService;

    // 대기열 진입. 이미 대기 중이면 현재 순번, 입장 완료면 토큰 반환.
    @PostMapping
    public ResponseEntity<WaitingResponse> enqueue(@PathVariable Long concertId,
                                                      @RequestParam Long userId) {

        EnqueueResult result = waitingService.enqueue(concertId, userId);

        // 대기 상태일 경우 신규 등록이므로 201 status 반환
        HttpStatus status = result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity.status(status).body(result.getResponse());
    }

    /**
     * GET /concerts/{concertId}/waiting/position?userId=1
     * 대기열 내 현재 순번 조회. 프론트가 3~5초 간격으로 폴링.
     */
    @GetMapping("/position")
    public ResponseEntity<WaitingResponse> getPosition(@PathVariable Long concertId,
                                                         @RequestParam Long userId) {

        return ResponseEntity.ok(waitingService.getPosition(concertId, userId));
    }
}
