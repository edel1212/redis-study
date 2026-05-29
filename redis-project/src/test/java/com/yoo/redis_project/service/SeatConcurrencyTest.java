package com.yoo.redis_project.service;

import com.yoo.redis_project.common.constants.RedisKeyConstants;
import com.yoo.redis_project.domain.entity.ConcertEntity;
import com.yoo.redis_project.domain.enums.SeatStatus;
import com.yoo.redis_project.domain.repository.ConcertRepository;
import com.yoo.redis_project.domain.seat.dto.SeatLockResponse;
import com.yoo.redis_project.domain.seat.entity.SeatEntity;
import com.yoo.redis_project.domain.seat.repository.SeatRepository;
import com.yoo.redis_project.domain.seat.service.SeatFacadeService;
import com.yoo.redis_project.domain.seat.service.SeatLockService;
import com.yoo.redis_project.domain.seat.service.SeatService;
import com.yoo.redis_project.domain.waiting.service.WaitingService;
import com.yoo.redis_project.support.RedisContainerSupport;
import com.yoo.redis_project.support.TestContainerSupport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Slf4j
@SpringBootTest
public class SeatConcurrencyTest extends TestContainerSupport {

    @Autowired
    private SeatFacadeService seatFacadeService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private WaitingService waitingService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Long testConcertId;
    private Long testSeatId;

    @BeforeEach
    void setUp() {
        // 테스트용 콘서트 + 좌석 준비
        ConcertEntity concert = concertRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("콘서트 없음"));

        testConcertId = concert.getId();

        SeatEntity seat = seatRepository.findAll().stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("AVAILABLE 좌석 없음"));

        testSeatId = seat.getId();
    }

    @Test
    @DisplayName("같은 좌석에 10명 동시 점유 — 실제 서비스 호출 — 1명만 성공")
    void concurrentAcquire_realService_onlyOneSucceeds() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        // 각 유저별 토큰 발급 (입장 완료 상태 만들기)
        Map<Long, String> tokenMap = new ConcurrentHashMap<>();
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            String token = UUID.randomUUID().toString();
            String enteredKey = RedisKeyConstants.WAITING_ENTERED.formatted(testConcertId);
            String tokenKey = RedisKeyConstants.WAITING_TOKEN.formatted(testConcertId, userId);

            stringRedisTemplate.opsForSet().add(enteredKey, String.valueOf(userId));
            stringRedisTemplate.opsForValue().set(tokenKey, token, Duration.ofMinutes(5));
            tokenMap.put(userId, token);
        }

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;

            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();  // 모든 스레드가 준비되면 동시에 시작

                    SeatLockResponse response = seatFacadeService
                            .acquireWithValidation(
                                    testConcertId,
                                    testSeatId,
                                    userId,
                                    tokenMap.get(userId));

                    if (response.isAcquired()) {
                        successCount.incrementAndGet();
                        log.info("성공 — userId={}", userId);
                    } else {
                        failCount.incrementAndGet();
                        log.info("실패 — userId={}, message={}", userId, response.getMessage());
                    }

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.info("예외 — userId={}, error={}", userId, e.getMessage());
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();    // 모든 스레드 준비 대기
        start.countDown(); // 동시 출발

        done.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("성공: {}, 실패: {}", successCount.get(), failCount.get());

        // 검증
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);

        // DB 상태 확인
        SeatEntity seat = seatRepository.findById(testSeatId).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
    }
}
