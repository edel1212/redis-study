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

## 특징
### 분산 락 쉬운 구현
- Pub/Sub 기반의 락 획득 : 락이 해제되면 채널을 통해 신호를 보내는 Pub/Sub 방식을 사용하므로 Redis의 부하가 훨씬 적음
- 타임아웃 및 Lease Time 지원 :  락을 획득하기 위해 대기하는 시간과, 락을 획득한 후 자동으로 해제되는 시간을 안전하게 지정할 수 있어 데드락을 방지
### 자바 컬렉션 프레임워크와의 연동
- Redisson의 `RMap`에 데이터를 넣으면, `java.util.Map`을 쓰는 것처럼 코드를 짜지만 실제 데이터는 Redis에 저장
- 동기화, 로킹 등이 내부적으로 처리되어 멀티스레드 및 분산 환경에서 안전

### 다양한 분산 객체 및 서비스 지원
- 객체들을 분산 환경용으로 제공
- 스케줄러 및 Executor: 분산 환경에서 실행되는 `RExecutorService`, `RScheduledExecutorService` 등을 통해 여러 서버에 작업을 분산하여 처리 가능


## Redisson 내부 — 왜 원자적인 이유
> Redis는 싱글 스레드이며, Lua 스크립트가 실행되는 동안 다른 어떤 명령어도 실행되지 않음

```text
[일반 명령어 — GET + DEL]

  Client → GET seat:1:lock        → Redis 실행 → 응답
  Client → DEL seat:1:lock        → Redis 실행 → 응답

  두 번 왕복 → 사이에 다른 명령어 끼어들 수 있음


[Lua 스크립트]

  Client → EVAL "스크립트 전체" → Redis가 스크립트를 한 덩어리로 실행 → 응답

  한 번 왕복 → 실행 중 다른 명령어 끼어들 수 없음
```

## Redis Server 분리 기준
> 분리 기준은 "용도"가 아니라 "장애 격리"이다.
> - 트래픽이 중/소 규모일 경우 1대로 진행하여도 문제가 없으나, 대규모 서비스에서는 분리하여 진행
>   - "maxmemory-policy" 설정으로 인한 eviction 정책이 충돌하기 때문에 분리 필요

### 인스턴스 분리 기준 예시
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
 
## Watchdog
> `lock.tryLock(waitTime, leaseTime, unit)` 에서 "leaseTime"를 -1로 설정하면 "watchdog" 활성화

### Watchdog 동작 흐름 
```text
  tryLock(5, -1, SECONDS)
      ↓
  락 획득 (기본 TTL 30초)
      ↓
  10초마다 체크: "이 스레드가 아직 살아있는가?"
      ├── 살아있음 → TTL 30초로 재설정
      └── 죽었음 (서버 다운) → 갱신 안 함 → 30초 후 자동 만료
      ↓
  unlock() 호출
      → Watchdog 중지
      → 락 즉시 해제
```

### leaseTime 전략 비교
| 분류 | `leaseTime` 고정 전략 (`tryLock(wait, lease, unit)`) | `Watchdog` 자동 연장 전략 (`tryLock(wait, -1, unit)`) |
| :--- | :--- | :--- |
| **기본 메커니즘** | 개발자가 지정한 시간(TTL)이 지나면 **무조건** 락 해제 | 쓰레드가 살아있는 한 10초마다 **무한 연장** (기본 30초) |
| **적합한 작업 특성** | **짧고 트래픽이 몰리는 작업** (수 ms ~ 수 초 이내 완료) | **길고 실행 시간이 가변적인 작업** (수십 초 ~ 수 분 이상) |
| **주요 실무 예시** | • 콘서트 좌석 선점 및 티켓 예매<br>• 선착순 쿠폰 발급 및 재고 차감<br>• 포인트 차감 및 결제 승인 API | • 대용량 데이터 일괄 정산 및 통계 배치(Batch)<br>• 대용량 파일 업로드 및 인코딩 작업<br>• 다중 서버 간 DB 마이그레이션 |
| **최대 장점** | **장애 격리 (Fail-Safe):** 로직 에러로 락이 안 풀려도 타임아웃 뒤 강제 회수되므로 **시스템 먹통을 방지**함. | **원자성 보장:** 작업이 예상보다 길어져도 중간에 락이 풀려 **데이터가 원자성을 잃고 꼬이는 현상을 차단**함. |
| **최악의 리스크** | **락 조기 유실:** 외부 API 지연 등으로 지정 시간보다 작업이 길어지면, 락이 풀려 **동시성 이슈가 발생**함. | **데드락 및 커넥션 고갈:** `unlock()` 누락 시 최장 30초간 락이 잔존하여 **대기 쓰레드가 급증하고 서버가 마비**됨. |
| **설계 핵심 초점** | **방어적 아키텍처:** 서비스 전체의 장애 전파 최소화 🎯 | **비즈니스 완결성:** 대량 태스크의 안전한 마무리 🎯 |

### 특징 ) 재진입 — 같은 스레드에서 두 번 획득 가능
> 스레드 ID + 재진입 카운터를 Redis에 저장하여, 같은 스레드면 카운트만 증가하고, unlock할 때 카운트가 0이 되어야 실제 해제함
> - 이러한 특징 떄문에 같은 동일 쓰레드가 자원을 선점하려할 때 "셀프 락"에 걸리지 않고 처리가 가능함
```java
public sample(){
  RLock lock = redissonClient.getLock("seat:1:lock");

  lock.tryLock(5, 300, TimeUnit.SECONDS);  // count: 1
  lock.tryLock(5, 300, TimeUnit.SECONDS);  // count: 2 (재진입 성공)

  lock.unlock();  // count: 1
  lock.unlock();  // count: 0 → 실제 해제    
}
```

### Redisson RLock — 내부 흐름
> 로직 전체가 한 번의 Lua 실행 안에서 처리
```text
lock.tryLock(3, 5, SECONDS)
        ↓
  Redisson이 Lua 스크립트를 Redis에 전송
        ↓
  ┌─────────────────────────────────────────────┐
  │  Redis 내부 (싱글 스레드, 원자적 실행)        │
  │                                              │
  │  락 키 존재하는가?                             │
  │    ├── 없음 → HSET + PEXPIRE → 획득 성공     │
  │    └── 있음 → 소유자가 같은가?                 │
  │               ├── 같음 → 카운트+1 (재진입)    │
  │               └── 다름 → 획득 실패             │
  └─────────────────────────────────────────────┘
        ↓
  결과 반환 (성공 / 실패 / 남은 TTL)
  
----------------------------------------------------------
----------------------------------------------------------

lock.unlock()
        ↓
  Redisson이 Lua 스크립트를 Redis에 전송
        ↓
  ┌─────────────────────────────────────────────┐
  │  Redis 내부 (싱글 스레드, 원자적 실행)        │
  │                                              │
  │  락 키 존재하는가?                             │
  │    ├── 없음 → 이미 만료됨 (무시)              │
  │    └── 있음 → 소유자가 나인가?                 │
  │               ├── 아님 → 해제 거부             │
  │               └── 맞음 → 카운트 -1            │
  │                          ├── 0 → DEL + Pub/Sub 알림  │
  │                          └── 1+ → TTL 재설정  │
  └─────────────────────────────────────────────┘
        ↓
  결과 반환
```
