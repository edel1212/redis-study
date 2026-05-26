# Redisson

## Redisson이란?
```text
Redis 클라이언트 라이브러리
  → 분산 락, 분산 컬렉션, 분산 서비스 등 고수준 API 제공
  → 내부적으로 Lua 스크립트 + Pub/Sub 조합으로 동작
```

## Lettuce vs Redisson 비교
| 항목 | Lettuce  | Redisson |
| :--- | :--- | :--- |
| **포지션** | 저수준 Redis 클라이언트 | 고수준 분산 프레임워크 |
| **Spring Boot 기본** | ✅ `spring-boot-starter-data-redis`에 포함 | ❌ 별도 의존성 추가 필요 |
| **주 용도** | GET/SET/ZADD 등 명령어 직접 실행 | 분산 락, 분산 컬렉션, 분산 서비스 |
| **분산 락** | 직접 구현 필요 (`SET NX EX` - 스핀 락 방식) | `RLock` API 제공 (Watchdog 자동 연장 포함) |
| **Lua 스크립트** | 직접 작성 및 관리 필요 | 내부적으로 자동 구현 및 사용 |
| **비동기 지원** | Reactive / Async | Reactive / Async / RxJava |

- 실무에서 가장 흔한 패턴은 `Lettuce + Redisson` **둘다 사용**한다.

## 핵심 특징과 장점
### 분산 락 쉬운 구현
- Pub/Sub 기반의 락 획득 : 락이 해제되면 채널을 통해 신호를 보내는 Pub/Sub 방식을 사용하므로 Redis의 부하가 훨씬 적음
- 타임아웃 및 Lease Time 지원 :  락을 획득하기 위해 대기하는 시간과, 락을 획득한 후 자동으로 해제되는 시간을 안전하게 지정할 수 있어 데드락을 방지
### 자바 컬렉션 프레임워크와의 연동
- Redisson의 `RMap`에 데이터를 넣으면, `java.util.Map`을 쓰는 것처럼 코드를 짜지만 실제 데이터는 Redis에 저장
- 동기화, 로킹 등이 내부적으로 처리되어 멀티스레드 및 분산 환경에서 안전

### 다양한 분산 객체 및 서비스 지원
- 객체들을 분산 환경용으로 제공
- 스케줄러 및 Executor: 분산 환경에서 실행되는 `RExecutorService`, `RScheduledExecutorService` 등을 통해 여러 서버에 작업을 분산하여 처리 가능

## Redis Server 분리 기준
> 분리 기준은 "용도"가 아니라 "장애 격리"이다.
> - 트래픽이 중/소 규모일 경우 1대로 진행하여도 문제가 없으나, 대규모 서비스에서는 분리하여 진행
>   - "maxmemory-policy" 설정으로 인한 eviction 정책이 충돌하기 때문에 분리 필요

### 인스턴스 분리 예시
```text
Redis A (캐시 전용)
  ├── 캐시 miss → DB 조회하면 복구 가능
  ├── 날아가도 서비스 가능 (지연만 발생)
  └── maxmemory-policy: allkeys-lru (메모리 부족 시 자동 삭제)

Redis B (락 + 대기열)
  ├── 데이터 유실 → 중복 점유, 대기열 붕괴
  ├── 날아가면 서비스 불가
  └── maxmemory-policy: noeviction (메모리 부족 시 쓰기 거부)
  
-------------------------------------------------------------------

[한 대로 운영할 때 발생 가능한 사고]
  캐시 데이터가 메모리 80% 차지
      ↓
  Redis가 메모리 확보를 위해 LRU 삭제 실행
      ↓
  락 키 / 대기열 키가 삭제됨 💥
      ↓
  좌석 중복 점유 + 대기열 붕괴
```

## 실무를 통한 문제 시나리오 
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

### 문제 코드
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

## SpringBoot 설정
```java
// build.gradle - Redisson 추가 필요
//implementation 'org.redisson:redisson-spring-boot-starter:4.4.0'

@Configuration
public class RedissonConfig {
    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 127.0.0.1 루프백 이슈를 우회하기 위해 명시적으로 "redis://localhost:6379" 주입
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort);

        return Redisson.create(config);
    }
}
```

##  RLock
### RLock이란?
```text
Redisson이 제공하는 분산 락 인터페이스
  → Redis 기반이지만 Java Lock처럼 사용
  → 내부적으로 Lua 스크립트 + Pub/Sub + Watchdog 조합
```

### Lock 메서드
- `lock.tryLock(waitTime, leaseTime, unit)` : 지정 시간 대기 후 락 시도 -  👍 실무 표준
  - **waitTime** : 다른 유저가 락을 잡고 있을 때 최대 대기 시간 (락 획득까지 최대 대기 시간)
  - **leaseTime** : 락 획득 후 자동 해제까지 시간 (락 유지 시간 [TTL])  🔸 "-1"로 설정할 경우 WatchDog으로 진행됨
  - **unit** : TimeUnit.SECONDS 와 같은 단위  
- `lock.lock()` : 락 획득까지 무한 대기 -  ⚠️ 데드락 위험
- `lock.unlock()` : 락 해제 (원자적) - 항상 finally에서 호출
 
