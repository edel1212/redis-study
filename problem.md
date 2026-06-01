# 진행간 이슈사항 정리

## 문제사항 1
> 점유된 좌석이 시간이 중간에 시간이 만료되어 다른 사람이 lock된것을 삭제하게 되는 경우
> - '락 오너십 분실 및 오발 삭제(Accidental Lock Release)'
```text
[유저 1]                              [유저 2]
    │                                     │
    ├─ GET seat:1:lock → "1" (나)         │
    │                                     │
    │   ← TTL 만료 → 락 소멸              │
    │                                     │
    │                                     ├─ SET NX seat:1:lock → "2" (획득)
    │                                     │
    ├─ DEL seat:1:lock                    │
    │   → 💥유저 2의 락을 삭제            │
    │                                     │
    │                                     ├─ 결제 시도 → 락 없음 → 실패
```

### 문제 예시 코드
> 간략함을 위해 코드 일부만 발췌함
```java
@Override
public boolean release(Long seatId, Long userId) {
    String seatLockKey   = RedisKeyConstants.SEAT_LOCK.formatted(seatId);

    // 사용자 요청의 좌석의 점유중인 사용자 조회
    String owner = stringRedisTemplate.opsForValue().get(seatLockKey);

    // 락 소유자와 호출 사용자가 같은지 비교
    if (!owner.equals( String.valueOf(userId) )) {
        log.warn("[SeatLock] 좌석 소유자 불일치. seatId={} 요청userId={} 실제owner={}",
                seatId, userId, owner);
        return false;
    } // if

    // *****************************************************************************************************************
    // 💥 해당 부분에서 만약 찰나의 순간 TTL 이 사라지고 "seatLockKey"에 새로운 사용자가 Lock이 될 경우 문제 발생
    // *****************************************************************************************************************
    
    // 소유자 일치 → 해제 [ 위의 경우 일치하지 않지만 삭제되는 문제가 발생해 버린다. ]
    stringRedisTemplate.delete(key);
    log.info("[SeatLock] 락 해제 완료. seatId={} userId={}", seatId, userId);
    return true;
  
}
```

## 문제사항 2
> Redisson 사용하여 분산락 시 @Transaction이 동작하지 않는다.
> - @Transaction은 AOP 레이어에서 동작하기 때문
```text
[실제 실행 순서]

Spring 프록시: 트랜잭션 시작 (BEGIN)
    ↓
  메서드 본문 실행 시작
    ↓
    RLock 획득
    DB 조회 + markHeld()
    finally { RLock 해제 }        ← 메서드 본문 안에서 실행
    return 응답
    ↓
  메서드 본문 실행 완료
    ↓
Spring 프록시: 트랜잭션 커밋 (COMMIT)   ← 메서드 밖에서 실행
```

### 문제 예시 코드
```java
@Transactional
    @Override
    public SeatLockResponse acquireWithValidation(Long concertId, Long seatId, Long userId, String token) {
  
        RLock lock = redissonClient.getLock(RedisKeyConstants.SEAT_MUTEX.formatted(seatId));
        boolean acquired = false;

        try{
            acquired = lock.tryLock(WAIT_TIME, -1 , TimeUnit.SECONDS);
            
            // 조건 처리
            if (!acquired) {
                return SeatLockResponse.fail(seatId, userId, "처리 중입니다. 잠시 후 다시 시도해주세요.");
            } // if

            // 🔍 entity update 부분        
            seat.markHeld();
            
            return SeatLockResponse.success(seatId, userId);

        } catch (InterruptedException e) {
            // TODO return error response
        } finally {
            // TODO unLock
        } // try - catch

    }
```

### 해결 방법
> Transaction 처리 메서드 Interface 분리
```text
10:00:00.000  RLock 획득
10:00:00.001  seatService.validateAndMarkHeld()
                  ↓
              @Transactional BEGIN
              findById → AVAILABLE
              validateAvailable → 통과
              markHeld → HELD
              @Transactional COMMIT   ← RLock 안에서 커밋 완료 
                  ↓
10:00:00.050  seatLockService.acquire → SET NX EX
10:00:00.051  return success
10:00:00.052  finally → RLock 해제     ← 커밋 후에 해제
```

## 문제사항 3
> Redis 자료구조의 한계
> - Set 방식 : 
>   - 장점 : 계산이 쉬움, 중복 방지, 구조 단순
>   - 단점 : values에 대한 TTL 설정 불가능하여 중도 이탈자 처리가 어려움
> - String(Value) 방식 :
>   - 장점 : 개별 TTL 가능하여 중도 이탈자 제거 가능
>   - 단점 : 현재 입장 인원 계산이 어려움, 전체 입장 사용자 관리가 어려움
```text
[사용자]
    │
    ├─ 1. 콘서트 대기 요청
    │
    ├─ 2. Redis 대기열(ZSet) 등록
    │      waiting:queue:{concertId}
    │
    │
[스케줄러]
    │
    ├─ 3. 대기열 사용자 순차 입장 처리
    │
    ├─ token 발급 (짧은 TTL) 
    │      waiting:token:{concertId}:{userId}
    │
    ├─ 입장 권한 부여(Set)
    │      waiting:entered:{concertId} {userId}
    │
    ▼
────────────────────────────────────────────

[문제 상황]

[사용자]
    │
    ├─ 4. 입장 후 좌석 선점 진행 안 함
    │      (중도 이탈 / 브라우저 종료 / 새로고침)
    │
    ├─ token key TTL 만료
    │      → 자동 제거됨
    │
    ├─ 그러나 entered key는 TTL 2시간 유지
    │
    ├─ 현재 입장 가능 사용자로 계속 계산됨
    │
    ├─ available = MAX_ENTRY_COUNT - currentEntered
    │
    ├─ 실제 사용자는 없지만
    │   입장 중인 사용자로 판단
    │
    └─ 💥 새로운 사용자 입장 불가능
        (유령 세션 문제 발생)
```

### 해결 방법
```text

[사용자]
    │
    ├─ 1. 콘서트 대기 요청
    │
    ├─ ZADD waiting:queue:{concertId}
    │      score = 요청 시간(timestamp)
    │      member = userId
    │
    ▼


┌────────────────────────────────────────────┐
│              processEntry()               │
│          (스케줄러 주기적 실행)            │
└────────────────────────────────────────────┘

    │
    ├─ 현재 시간 조회
    │
    ├─ long now = System.currentTimeMillis()
    │
    ▼


[Step 1. 만료된 entered 사용자 정리]

    │
    ├─ ZREMRANGEBYSCORE
    │      waiting:entered:{concertId}
    │      0 ~ now-1 범위 삭제
    │
    ├─ score(expireTime)가 현재 시간보다 작은 사용자 제거
    │
    └─ 중도 이탈 / 만료 사용자 cleanup 완료
    │
    ▼


[Step 2. 현재 입장 가능 인원 계산]

    │
    ├─ ZCOUNT enteredKey now +inf
    │
    ├─ score >= now 인 사용자만 집계
    │
    ├─ 현재 유효 입장 사용자 수 계산
    │
    ├─ available =
    │      MAX_ENTRY_COUNT - currentEntered
    │
    └─ 빈 자리 수 계산
    │
    ▼


[입장 가능 여부 판단]

    │
    ├─ available <= 0
    │      └─ 종료
    │
    └─ available > 0
           ▼


[Step 3. 대기열 사용자 입장 처리]

    │
    ├─ ZPOPMIN queueKey available
    │
    ├─ 대기 순서가 가장 빠른 사용자 추출
    │
    └─ 입장 대상 사용자 목록 확보
    │
    ▼


[사용자별 입장 처리]

    │
    ├─ UUID 토큰 생성
    │
    ├─ SET waiting:token:{concertId}:{userId}
    │      value = UUID
    │      TTL = TOKEN_TTL
    │
    ├─ score 계산
    │
    ├─ expiryScore =
    │      currentTime + TOKEN_TTL
    │
    ├─ ZADD waiting:entered:{concertId}
    │      score = expiryScore
    │      member = userId
    │
    └─ 입장 가능 사용자 등록
    │
    ▼


[입장 상태 유지]

    │
    ├─ enteredKey 자체 TTL 설정
    │
    ├─ EXPIRE enteredKey ENTERED_TTL
    │
    └─ 전체 key orphan 방지
```

## 문제사항 4
> DB 좌석 결제 완료가 commit -> Redis 좌석 선점 관련 Key 정리 완료 -> 커밋 시도 💥 시 에러로 인한 롤백
> - 해당 문제는 가장 큰 이슈가 되는 시나리오 좌석이 SOLD 되고 Reids Key 정리가 안되면 TTL로 인해 처리가 되지만 해당 문제는 CS문제까지 갈 수 있음

### 문제 코드
```java
@Transactional
@Override
public BookingResponse confirm(Long seatId, Long userId) {

    // 지정 좌석 조회 - 점유자 조회
    Optional<Long> owner = seatLockService.getLockOwner(seatId);

    if (owner.isEmpty() || !owner.get().equals(userId)) {
        return BookingResponse.fail(seatId, "좌석 점유 상태가 아니거나 소유자가 다릅니다.");
    } // if

    // DB 좌석 조회
    SeatEntity seat = seatRepository.findById(seatId)
            .orElseThrow(() -> new ResourceNotFoundException("좌석을 찾을 수 없습니다."));

    // 좌석 상태 업데이트
    seat.markAsSold();
    seatRepository.save(seat);
    
    // 입장 자리 반환 (entered + token 제거)
    Long concertId = seat.getConcert().getId();
    waitingService.releaseEntry(concertId, userId);
    
    // ⑤ 좌석 락 해제
    seatLockService.release(seatId, userId);

    // 💥 ← 여기서 실패 (DB 락 타임아웃, 제약조건, 커넥션 끊김 등)
    //  └─ DB 롤백 → seat는 다시 HELD/AVAILABLE
    // 그러나 Redis 토큰·락은 이미 삭제됨
    //  → 유저는 좌석을 못 샀는데, 재시도할 토큰마저 잃음
    //  → "결제됐는데 자리가 없다 + 다시 들어갈 수도 없다"

    log.info("예매 확정 seatId={}, userId={}", seatId, userId);
    return BookingResponse.success(seatId, userId);
}
```

### 문제 흐름
"문제사항 2"와 비슷한 이유 하지만 더 크리티컬한 이유
- 1시간을 기다려 입장이 되었고 좌석 선점 및 구매까지 진행했으나 rollback 되는 경우

### 개선 코드
- `@Transaction` 제거
- Reids Key 제거 부분 분리 - 문제가 발생해도 TTL을 통해 자가 회복이 가능
```java
@Override
public BookingResponse confirm(Long seatId, Long userId) {

    // 지정 좌석 조회 - 점유자 조회
    Optional<Long> owner = seatLockService.getLockOwner(seatId);

    if (owner.isEmpty() || !owner.get().equals(userId)) {
        return BookingResponse.fail(seatId, "좌석 점유 상태가 아니거나 소유자가 다릅니다.");
    } // if

    // 자리 선점 및 대상 ConcertID 반환 - 구조적 문제 학습용이기에 단일 기반으로 잡혀있음
    Long concertId = bookingService.markAsSold(seatId);

    // 3) 커밋 성공 후 Redis 정리 (best-effort, 자가 치유 대상)
    try {
        waitingService.releaseEntry(concertId, userId);
        seatLockService.release(seatId, userId);
    } catch (Exception e) {
        // 좌석은 이미 SOLD로 확정됨(source of truth = DB).
        // lock(TTL) / token(TTL) / entered(removeRangeByScore 스케줄러)로 자가 치유되므로
        // 정리 실패를 예매 실패로 전파하지 않는다.
        log.error("예매 확정 후 Redis 정리 실패(자가 치유 대상) seatId={}, userId={}",
                seatId, userId, e);
    }

    log.info("예매 확정 seatId={}, userId={}", seatId, userId);
    return BookingResponse.success(seatId, userId);
}
```

