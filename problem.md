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