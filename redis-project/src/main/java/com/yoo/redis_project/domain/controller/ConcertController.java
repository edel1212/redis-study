package com.yoo.redis_project.domain.controller;

import com.yoo.redis_project.domain.dto.ConcertDto;
import com.yoo.redis_project.domain.dto.SeatDto;
import com.yoo.redis_project.domain.service.ConcertQueryService;
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

    @GetMapping("/{concertId}")
    public ResponseEntity<ConcertDto> getConcert(@PathVariable Long concertId) {
        // 조회 수 증가
        concertViewService.increment(concertId);
        return ResponseEntity.ok(concertQueryService.getConcert(concertId));
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
}
