package com.yoo.redis_project.domain.controller;

import com.yoo.redis_project.domain.dto.ConcertDto;
import com.yoo.redis_project.domain.dto.ConcertRankingEntryResponse;
import com.yoo.redis_project.domain.dto.SeatDto;
import com.yoo.redis_project.domain.service.ConcertQueryService;
import com.yoo.redis_project.domain.service.ConcertRankingService;
import com.yoo.redis_project.domain.service.ConcertViewService;
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

    @GetMapping("/{concertId}")
    public ResponseEntity<ConcertDto> getConcert(@PathVariable Long concertId) {
        // 콘서트 정보 조회
        ConcertDto concert = concertQueryService.getConcert(concertId);
        // 조회 수 증가
        concertViewService.increment(concertId);
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
    public ResponseEntity<List<ConcertRankingEntryResponse>> getRanking(
            // 기본값 10
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(rankingService.getTopRanking(size));
    }
}
