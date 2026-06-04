# Reids


## Redis란?
> Redis (Remote Dictionary Server)
- 메모리에 데이터를 저장하는 `In-Memory` 데이터 저장소

| 구분 | MySQL (RDB) | Redis (In-Memory) |
| :--- | :--- | :--- |
| **저장 위치** | 디스크 (HDD/SSD) | **메모리 (RAM)** |
| **처리 속도** | 상대적으로 느림 (밀리초) | **매우 빠름 (마이크로초)** |
| **데이터 영속성** | **영구 저장** (전원 종료 시 유지) | **기본 휘발성** (설정에 따라 저장 가능) |
| **데이터 형태** | 정형화된 테이블 (Row/Column) | Key-Value 및 다양한 자료구조(List, Set 등) |
| **주요 용도** | 서비스 핵심 데이터 저장, 복잡한 연산 | **캐싱**, 세션 관리, 실시간 랭킹, 메시지 큐 |

### 싱글 스레드
> Redis는 기본적으로 **싱글 스레드**이다.
- 명령어를 **한 번에 하나씩 순서대로 처리**
- Redis 6.0 이후 멀티스레드가 일부 도입됐지만 **명령어 처리 자체는 여전히 싱글 스레드 형태**
  - 네트워크 I/O 처리 부분만 멀티스레드로 개선

#### 빠른 이유?
> 일반적으로 싱글 스레드 = 느리다고 생각하지만 Redis는 빠르다.
- 메모리 기반
- 컨텍스트 스위칭 없음
- I/O Multiplexing : 위에서 말한 6.0 이후 멀티스레드가 일부 도입

#### 싱글 스레드이기 때문의 장/단점
- 👍 장점
  - 명령어가 순서대로 처리로 **Race Condition 없음** -> 별도의 Lock 없이도 데이터 정합성 보장
    - **경쟁 상태가 없음**
- 👎 단점
  - 하나의 명령어가 오래 걸리면 나머지 전부 대기
    - 예:) `KEYS *` 처럼 전체 키를 스캔하는 명령어 → **서비스 전체 지연 발생** 
    - `KEYS *  // ❌ 100만 건 전체 스캔 → 그동안 다른 요청 전부 대기`
    - `SCAN    // ✅ 조금씩 나눠서 스캔 → 다른 요청 영향 없음`

## 대표 사용 사례
```text
1. 캐싱     → DB 부하를 줄이기 위해 **자주 조회되는 데이터**를 Redis에 저장
2. 세션     → 로그인 **세션을 서버 메모리 대신 Redis에 저장** (서버 여러 대일 때 유용)
3. 랭킹     → 실시간 점수 **정렬**이 필요할 때
4. 분산 락  → 여러 서버에서 동시에 같은 **자원에 접근할 때 충돌 방지**
5. 메시지   → 서비스 간 **간단한 메시지 전달**
```

## maxmemory-policy (메모리 정책) 설정 옵션
> 메모리가 가득 찼을 경우 진행 방향 설정
>  - noeviction (기본값)

| 정책명 | 삭제 대상 | 추천 시나리오 | 특징 및 주의사항 |
| :--- | :--- | :--- | :--- |
| **`allkeys-lru`** | **전체** 키 중 <br>가장 오래전 사용된 것 | **⭐️ 일반적인 캐시 서버** | **실무 표준.** 최근 사용 데이터가 다시 쓰일 확률이 높을 때 최적. |
| **`allkeys-lfu`** | **전체** 키 중 <br>사용 빈도가 가장 낮은 것 | 사용 패턴이 일정한 서비스 | 최근 사용 여부보다 **얼마나 자주** 쓰였는지가 중요할 때 유리. |
| **`volatile-lru`** | **TTL 설정된** 키 중 <br>가장 오래전 사용된 것 | DB 보조용 임시 캐시 | TTL이 없는 영구 데이터는 보호됨. 단, **공간 부족 시 에러 위험.** |
| **`volatile-lfu`** | **TTL 설정된** 키 중 <br>사용 빈도가 가장 낮은 것 | 만료 시간이 있는 데이터 위주 | TTL이 있는 키들 사이에서 자주 쓰이는 것만 남기고 싶을 때 사용. |
| **`volatile-ttl`** | **TTL 설정된** 키 중 <br>남은 시간이 가장 적은 것 | 만료 시점 관리가 핵심일 때 | 논리적으로 **곧 삭제될 데이터부터** 미리 지워 메모리 확보. |
| **`noeviction`** | **삭제 안 함** | 데이터 보존이 필수일 때 | 메모리 가득 차면 **쓰기 작업 시 에러(OOM)** 반환. |
| **`allkeys-random`** | 전체 키 중 랜덤 삭제 | 성능이 최우선일 때 | 알고리즘 연산을 최소화하고 싶을 때 사용하나 실무에선 드묾. |
 
### 메모리 정책 설정 시 주요 사항
- 적정 `maxmemory` 값
  - Redis 단독 서버 : 시스템 메모리의 `60~70%`
  - 다른 프로세스와 공존 : 시스템 메모리의 `30~40%`
```yaml
# ❌ 잘못된 설정 (정책만 있고 메모리 한계 없음)
maxmemory 0          # 메모리 무제한
maxmemory-policy allkeys-lru  # 메모리 꽉 차면 오래된 키 삭제

#------- 위의 설정일 경우 흐름 -------

# maxmemory 0 = 메모리 한도 없음
#        ↓
# 메모리가 꽉 찰 일이 없음
        ↓
# maxmemory-policy 가 동작할 일이 없음
#        ↓
# allkeys-lru 설정이 의미없는 상태
#---------------------------------

# ✅ 올바른 설정
maxmemory 5gb
maxmemory-policy allkeys-lru
```

## Persistence (RDB/AOF) 설정 가이드
> 💡 Redis에 없어서는 안 될 데이터를 저장하는 설계 자체가 잘못된 것이다.
> - Redis는 보조 저장소이고 원본 데이터는 항상 RDB(MySQL 등)에 있어야 함
> - 캐시 용도라면 Persistence를 꺼도 전혀 문제없는 구조가 맞음

| 용도 | RDB | AOF | 이유 |
| :--- | :---: | :---: | :--- |
| **캐시 전용** | ❌ | ❌ | 데이터 유실 시 DB에서 다시 로드하면 됨 (성능 중시) |
| **세션 관리** | ✅ | ❌ | 어느 정도의 유실은 허용 가능 |
| **랭킹 / 카운터** | ✅ | ✅ | 데이터 유실 최소화가 필수적임 |
| **분산 락** | ❌ | ❌ | TTL 기반이라 재시작 시 Persistence 의미 없음 |

### RDB (Redis Database)
> 설치 시 기본 값으로 세팅되어 있다.
- `config get save` 명령어를 통해 설정 정보를 받아 올 수 있음
  - ex) `{{지정시간}} {{변경 횟수}}` 에 맞으면 저장

#### Flow
```text
메모리 상태
[name:yoo, age:20, gender:man]
      ↓ 특정 시점에 파일로 저장
dump.rdb 파일 생성
      ↓ 서버 재시작 시
dump.rdb 파일로 복구
```
#### RDB 저장 조건 설정 (redis.conf)
```text
# 아래 조건 중 하나라도 충족되면 스냅샷 저장
save 900 1      # 900초 내 1번 이상 변경 시
save 300 10     # 300초 내 10번 이상 변경 시
save 60 10000   # 60초 내 10000번 이상 변경 시
```
#### 장/단점
- 👍장점
  - 파일 크기가 작고 복구 속도가 빠름 
  - 서버 성능에 영향이 적음
- 👎단점
  - 스냅샷을 저장하는 **시간 사이에 장애 나면 그 사이 데이터 유실**

### AOF (Append Only File)
> 모든 쓰기 명령어를 로그 파일에 순서대로 기록하는 방식

#### Flow
```text
SET name yoo  → appendonly.aof 에 기록
SET age 20    → appendonly.aof 에 기록
DEL name      → appendonly.aof 에 기록
      ↓ 서버 재시작 시
명령어를 처음부터 순서대로 재실행하여 복구
```

#### 저장 설정
```text
appendfsync always    # 매 명령어마다 저장 (가장 안전, 가장 느림)
appendfsync everysec  # 1초마다 저장 (권장 ✅)
appendfsync no        # OS가 알아서 저장 (가장 빠름, 가장 위험)
```

#### 장/단점
- 👍장점 : 데이터 유실 최소화 (최대 1초치만 유실)
- 👎단점 :
  - 파일 크기가 커지고 복구 속도가 느림
  - 서버 성능에 상대적으로 더 영향을 줌 


## Cache Aside 패턴
> 갱신(SET)의 경우 왜 삭제(DELETE)인가?
> - 삭제 후 다음 읽기 요청에서 최신 DB 값을 다시 적재하는 것이 안전함
>   -  Cache Aside (Lazy Loading) 의 핵심
```text
[읽기]
호출자
  → Redis GET
      ├── 히트  → 반환 (끝)
      └── 미스  → DB 조회
                  → Redis SET (TTL 포함)
                  → 반환

[쓰기 / 갱신]
호출자
  → DB UPDATE
  → Redis DELETE (캐시 무효화)  ← 갱신이 아니라 삭제가 정석
```

## 📎 부록 — 전체 문서 인덱스

- [data-structure.md](./data-structure.md) — 자료구조 & 명령어 & TTL & 네이밍
- [redis-with-spring-boot.md](./redis-with-spring-boot.md) — Spring Boot 연동
- [ttl-strategy.md](./ttl-strategy.md) — TTL 전략 (Stampede / Jitter / 동적 TTL)
- [redisson.md](./redisson.md) — Redisson & RLock & Watchdog
- [pub-sub.md](./pub-sub.md) — Pub/Sub & Streams
- [shed-lock.md](./shed-lock.md) — 분산 스케줄러
- [data-consistency.md](./data-consistency.md) — 2PC / Saga / Outbox
- [sentinel.md](./sentinel.md) — 고가용성 / Failover
- [problem.md](./problem.md) — 진행 중 만난 이슈 모음
- [make-use-of.md](./make-use-of.md) — 실전 활용 패턴