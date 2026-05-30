package com.yoo.redis_project.domain.concert;

import com.yoo.redis_project.domain.concert.dto.ConcertDto;
import com.yoo.redis_project.domain.concert.dto.ConcertRankingDetailResponse;
import com.yoo.redis_project.domain.seat.dto.SeatDto;
import com.yoo.redis_project.domain.concert.service.ConcertQueryService;
import com.yoo.redis_project.domain.concert.service.ConcertRankingService;
import com.yoo.redis_project.domain.concert.service.ConcertViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/concerts")
public class ConcertController {

    private final ConcertQueryService concertQueryService;
    private final ConcertViewService concertViewService;
    private final ConcertRankingService rankingService;

    // 실무에서는 userId를 쿼리 파라미터가 아닌 인증 토큰(SecurityContext) 에서 추출
    @GetMapping("/{concertId}")
    public ResponseEntity<ConcertDto> getConcert(@PathVariable Long concertId, @RequestParam Long userId) {
        // 콘서트 정보 조회
        ConcertDto concert = concertQueryService.getConcert(concertId);
        // 조회 수 증가
        concertViewService.increment(concertId, userId);
        return ResponseEntity.ok(concert);
    }

    @GetMapping("/{concertId}/seats")
    public ResponseEntity<List<SeatDto>> getSeats(@PathVariable Long concertId) {
        return ResponseEntity.ok(concertQueryService.getSeats(concertId));
    }

    @DeleteMapping("/{concertId}/cache")
    public ResponseEntity<Void> evictCache(@PathVariable Long concertId) {
        concertQueryService.evictConcertCache(concertId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ranking")
    public ResponseEntity<List<ConcertRankingDetailResponse>> getRanking(
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(rankingService.getTopRankingWithDetail(size));
    }
}
