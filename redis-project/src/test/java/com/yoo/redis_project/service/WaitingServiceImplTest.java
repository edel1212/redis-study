package com.yoo.redis_project.service;

import com.yoo.redis_project.common.constants.RedisKeyConstants;
import com.yoo.redis_project.domain.waiting.dto.EnqueueResult;
import com.yoo.redis_project.domain.waiting.dto.WaitingResponse;
import com.yoo.redis_project.domain.waiting.service.WaitingService;
import com.yoo.redis_project.enums.WaitingStatus;
import com.yoo.redis_project.exception.custom.BadRequestException;
import com.yoo.redis_project.support.TestContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class WaitingServiceImplTest extends TestContainerSupport {

    @Autowired
    private WaitingService waitingService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final Long CONCERT_ID = 1L;
    private static final Long USER_ID    = 100L;
    private static final Long USER_ID_2  = 101L;

    // =================== enqueue() ===================

    @Test
    @DisplayName("신규 사용자 enqueue - 대기열 등록 및 position 1 반환")
    void enqueue_newUser_returnsNewWaiting() {
        EnqueueResult result = waitingService.enqueue(CONCERT_ID, USER_ID);

        assertThat(result.isCreated()).isTrue();
        assertThat(result.getResponse().getStatus()).isEqualTo(WaitingStatus.WAITING);
        assertThat(result.getResponse().getPosition()).isEqualTo(1);
    }

    @Test
    @DisplayName("동일 사용자 재 enqueue - 기존 대기 순서 반환")
    void enqueue_sameUser_returnsExistingWaiting() {
        waitingService.enqueue(CONCERT_ID, USER_ID);

        EnqueueResult second = waitingService.enqueue(CONCERT_ID, USER_ID);

        assertThat(second.isCreated()).isFalse();
        assertThat(second.getResponse().getStatus()).isEqualTo(WaitingStatus.WAITING);
        assertThat(second.getResponse().getPosition()).isEqualTo(1);
    }

    @Test
    @DisplayName("다중 사용자 enqueue - 진입 순서대로 position 부여")
    void enqueue_multipleUsers_positionIncreases() {
        EnqueueResult first  = waitingService.enqueue(CONCERT_ID, USER_ID);
        EnqueueResult second = waitingService.enqueue(CONCERT_ID, USER_ID_2);

        assertThat(first.getResponse().getStatus()).isEqualTo(WaitingStatus.WAITING);
        assertThat(second.getResponse().getStatus()).isEqualTo(WaitingStatus.WAITING);
        assertThat(second.getResponse().getPosition())
                .isGreaterThanOrEqualTo(first.getResponse().getPosition());
    }

    @Test
    @DisplayName("입장 완료 사용자 enqueue - 유효 토큰 반환")
    void enqueue_enteredUserWithValidToken_returnsEntered() {
        String token      = UUID.randomUUID().toString();
        String enteredKey = RedisKeyConstants.WAITING_ENTERED.formatted(CONCERT_ID);
        String tokenKey   = RedisKeyConstants.WAITING_TOKEN.formatted(CONCERT_ID, USER_ID);

        long futureScore = System.currentTimeMillis() + Duration.ofMinutes(6).toMillis();
        redisTemplate.opsForZSet().add(enteredKey, String.valueOf(USER_ID), futureScore);
        redisTemplate.opsForValue().set(tokenKey, token, Duration.ofMinutes(6));

        EnqueueResult result = waitingService.enqueue(CONCERT_ID, USER_ID);

        assertThat(result.getResponse().getStatus()).isEqualTo(WaitingStatus.ENTERED);
        assertThat(result.getResponse().getToken()).isEqualTo(token);
    }

    @Test
    @DisplayName("entered ZSet의 score가 만료된 사용자 enqueue - 대기열 신규 등록")
    void enqueue_enteredUserWithExpiredScore_treatsAsNotEntered() {
        String enteredKey = RedisKeyConstants.WAITING_ENTERED.formatted(CONCERT_ID);
        long pastScore    = System.currentTimeMillis() - 10_000;
        redisTemplate.opsForZSet().add(enteredKey, String.valueOf(USER_ID), pastScore);

        EnqueueResult result = waitingService.enqueue(CONCERT_ID, USER_ID);

        assertThat(result.getResponse().getStatus()).isEqualTo(WaitingStatus.WAITING);
    }

    @Test
    @DisplayName("score는 유효하지만 tokenKey 없는 사용자 enqueue - NOT_IN_QUEUE 반환")
    void enqueue_enteredUserWithValidScoreButNoToken_returnsNotInQueue() {
        String enteredKey = RedisKeyConstants.WAITING_ENTERED.formatted(CONCERT_ID);
        long futureScore  = System.currentTimeMillis() + Duration.ofMinutes(6).toMillis();
        redisTemplate.opsForZSet().add(enteredKey, String.valueOf(USER_ID), futureScore);
        // tokenKey 미설정

        EnqueueResult result = waitingService.enqueue(CONCERT_ID, USER_ID);

        assertThat(result.getResponse().getStatus()).isEqualTo(WaitingStatus.NOT_IN_QUEUE);
        assertThat(result.isCreated()).isFalse();
    }

    // =================== getPosition() ===================

    @Test
    @DisplayName("미등록 사용자 getPosition - NOT_IN_QUEUE 반환")
    void getPosition_userNotInQueue_returnsNotInQueue() {
        WaitingResponse response = waitingService.getPosition(CONCERT_ID, USER_ID);

        assertThat(response.getStatus()).isEqualTo(WaitingStatus.NOT_IN_QUEUE);
    }

    @Test
    @DisplayName("대기열 사용자 getPosition - WAITING 및 position 반환")
    void getPosition_userInQueue_returnsWaiting() {
        waitingService.enqueue(CONCERT_ID, USER_ID);

        WaitingResponse response = waitingService.getPosition(CONCERT_ID, USER_ID);

        assertThat(response.getStatus()).isEqualTo(WaitingStatus.WAITING);
        assertThat(response.getPosition()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("입장 완료 사용자 getPosition - ENTERED 및 token 반환")
    void getPosition_enteredUserWithValidToken_returnsEntered() {
        String token      = UUID.randomUUID().toString();
        String enteredKey = RedisKeyConstants.WAITING_ENTERED.formatted(CONCERT_ID);
        String tokenKey   = RedisKeyConstants.WAITING_TOKEN.formatted(CONCERT_ID, USER_ID);

        long futureScore = System.currentTimeMillis() + Duration.ofMinutes(6).toMillis();
        redisTemplate.opsForZSet().add(enteredKey, String.valueOf(USER_ID), futureScore);
        redisTemplate.opsForValue().set(tokenKey, token, Duration.ofMinutes(6));

        WaitingResponse response = waitingService.getPosition(CONCERT_ID, USER_ID);

        assertThat(response.getStatus()).isEqualTo(WaitingStatus.ENTERED);
        assertThat(response.getToken()).isEqualTo(token);
    }

    // =================== processEntry() ===================

    @Test
    @DisplayName("processEntry - 대기 사용자를 entered ZSet에 승격하고 tokenKey 생성")
    void processEntry_promotesWaitingUserToEntered() {
        waitingService.enqueue(CONCERT_ID, USER_ID);

        waitingService.processEntry(CONCERT_ID);

        String enteredKey = RedisKeyConstants.WAITING_ENTERED.formatted(CONCERT_ID);
        String tokenKey   = RedisKeyConstants.WAITING_TOKEN.formatted(CONCERT_ID, USER_ID);

        Double score = redisTemplate.opsForZSet().score(enteredKey, String.valueOf(USER_ID));
        assertThat(score).isNotNull();
        assertThat(score).isGreaterThan(System.currentTimeMillis());

        String token = redisTemplate.opsForValue().get(tokenKey);
        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("processEntry - 만료된 entered 멤버 정리 후 대기 사용자 승격")
    void processEntry_removesExpiredEnteredAndPromotesFromQueue() {
        String enteredKey  = RedisKeyConstants.WAITING_ENTERED.formatted(CONCERT_ID);
        String expiredUser = "999";
        long pastScore     = System.currentTimeMillis() - 10_000;
        redisTemplate.opsForZSet().add(enteredKey, expiredUser, pastScore);

        waitingService.enqueue(CONCERT_ID, USER_ID);
        waitingService.processEntry(CONCERT_ID);

        Double expiredScore = redisTemplate.opsForZSet().score(enteredKey, expiredUser);
        assertThat(expiredScore).isNull();

        Double userScore = redisTemplate.opsForZSet().score(enteredKey, String.valueOf(USER_ID));
        assertThat(userScore).isNotNull();
    }

    @Test
    @DisplayName("processEntry - entered 50명 가득찼을 때 대기 사용자 승격 안됨")
    void processEntry_respectsMaxEntryCount() {
        String enteredKey = RedisKeyConstants.WAITING_ENTERED.formatted(CONCERT_ID);
        long futureScore  = System.currentTimeMillis() + Duration.ofHours(1).toMillis();

        for (int i = 1; i <= 50; i++) {
            redisTemplate.opsForZSet().add(enteredKey, String.valueOf(i), futureScore);
        }

        waitingService.enqueue(CONCERT_ID, USER_ID);
        waitingService.processEntry(CONCERT_ID);

        Double score = redisTemplate.opsForZSet().score(enteredKey, String.valueOf(USER_ID));
        assertThat(score).isNull();
    }

    // =================== releaseEntry() ===================

    @Test
    @DisplayName("releaseEntry - entered ZSet에서 제거 및 tokenKey 삭제")
    void releaseEntry_removesUserFromEnteredAndDeletesToken() {
        String enteredKey = RedisKeyConstants.WAITING_ENTERED.formatted(CONCERT_ID);
        String tokenKey   = RedisKeyConstants.WAITING_TOKEN.formatted(CONCERT_ID, USER_ID);
        String token      = UUID.randomUUID().toString();

        long futureScore = System.currentTimeMillis() + Duration.ofMinutes(6).toMillis();
        redisTemplate.opsForZSet().add(enteredKey, String.valueOf(USER_ID), futureScore);
        redisTemplate.opsForValue().set(tokenKey, token, Duration.ofMinutes(6));

        waitingService.releaseEntry(CONCERT_ID, USER_ID);

        assertThat(redisTemplate.opsForZSet().score(enteredKey, String.valueOf(USER_ID))).isNull();
        assertThat(redisTemplate.opsForValue().get(tokenKey)).isNull();
    }

    // =================== validateToken() ===================

    @Test
    @DisplayName("validateToken - 일치하는 token이면 true 반환")
    void validateToken_matchingToken_returnsTrue() {
        String token    = UUID.randomUUID().toString();
        String tokenKey = RedisKeyConstants.WAITING_TOKEN.formatted(CONCERT_ID, USER_ID);
        redisTemplate.opsForValue().set(tokenKey, token, Duration.ofMinutes(6));

        boolean result = waitingService.validateToken(CONCERT_ID, USER_ID, token);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("validateToken - 잘못된 token이면 false 반환")
    void validateToken_wrongToken_returnsFalse() {
        String token    = UUID.randomUUID().toString();
        String tokenKey = RedisKeyConstants.WAITING_TOKEN.formatted(CONCERT_ID, USER_ID);
        redisTemplate.opsForValue().set(tokenKey, token, Duration.ofMinutes(6));

        boolean result = waitingService.validateToken(CONCERT_ID, USER_ID, "wrong-token");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("validateToken - tokenKey 없으면 BadRequestException 발생")
    void validateToken_noToken_throwsBadRequestException() {
        assertThatThrownBy(() -> waitingService.validateToken(CONCERT_ID, USER_ID, "any"))
                .isInstanceOf(BadRequestException.class);
    }
}
