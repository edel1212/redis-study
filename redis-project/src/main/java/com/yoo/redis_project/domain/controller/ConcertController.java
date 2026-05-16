package com.yoo.redis_project.domain.controller;

import com.yoo.redis_project.domain.dto.ConcertDto;
import com.yoo.redis_project.domain.dto.SeatDto;
import com.yoo.redis_project.domain.service.ConcertQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/concerts")
public class ConcertController {

    private final ConcertQueryService concertQueryService;

    @GetMapping("/{concertId}")
    public ResponseEntity<ConcertDto> getConcert(@PathVariable Long concertId) {
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
