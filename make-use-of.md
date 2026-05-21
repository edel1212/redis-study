# 활용

## Redis | MySQL 직접 UPDATE 방식 비교

| 구분 | MySQL 직접 UPDATE | Redis 도입 |
| :--- | :--- | :--- |
| **성능** | 디스크 I/O 발생, 트래픽 급증 시 응답 지연 | 인메모리 처리, 밀리초 단위 응답 |
| **동시성** | Lock 경합으로 인한 성능 저하 및 Race Condition | 원자적 연산(INCR 등)을 통한 동시성 제어 |
| **부하** | 매 요청마다 DB 부하 가중 | DB 부하 분산 및 배치 처리 가능 |
| **비고** | 데이터 유실 위험 낮음 (정합성 우선) | 고성능/고가용성 우선 (Write-Back) |

### 👎 RDB Only Use Bad Flow
```text
게시글 인기글 기준으로 
동시에 1000명이 조회
        ↓
MySQL에 직접 UPDATE 1000번
        ↓
DB 부하 💀
```

## 게시글 조회 수 [INCR 활용]

### Redis Use Flow
> 조회용 Key, 증감용 Key를 두고 캐싱 하여 응답
> - 증감용 Key는 INCR 할 때마다 TTL을 신규 시간 갱신해주는것이 포인트 (메모리 관리)
```text
GET /concerts/{{id}}  [지정 게시물 조회 요청]
  │
  ├─ increment({{id}})  [증감용 캐시 증감 및 TTL 갱신]
  │    └─ INCR concert:1:views:delta
  │    └─ EXPIRE concert:1:views:delta 3600
  │
  └─ getConcertOrThrow(1L) [조회용 캐시 Hit or Miss]
       ├─ 캐시 히트 → ConcertDto (현재 views) 반환
       └─ 캐시 미스 → DB 조회 → 캐시 적재 → 반환

--------------------------------------------------------------------
# 스케줄링 방식일 경우
[10분마다 - 스케줄러]
  └─ getAndSet(delta, "0") → 42                             [ 증감용 값을 끄내오며, 값을 0으로 초기화 ]
  └─ concert.addViews(42)  → Dirty Checking → DB UPDATE     [DB 업데이트 진행]
```

### 왜 Redis로 카운터를 만드는가
> Redis는 단일 스레드로 명령어를 처리함.
> - `INCR`은 Read-Modify-Write 가 **원자적**으로 실행되므로 Race Condition이 구조적으로 불가능하다.
```text
👎 DB로 카운터 구현 시: 
  UPDATE concerts SET view_count = view_count + 1 WHERE id = 1
  → 동시 요청 1000개 → DB 락 경합 → 병목
  → 인기 콘서트 조회 폭주 시 DB 다운 위험

👎 Redis INCR: 
  INCR concert:1:views
  → 단일 스레드 처리 → 락 없음 → 원자적 보장
  → 초당 수십만 건 처리 가능
```

### 참고 사항

#### 두 개의 캐시(조회용/증감용) 사용에 따른 데이터 지연 (Eventual Consistency)
- 증감용 키의 DB반영 배치 주기를 줄이면 **DB 반영은 빨라**지지만, 조회 요청 시 조회용 캐시(TTL)가 계속 **Hit되면 과거의 조회수**가 보임
- 선택 사항 (3개)
  - 조회 시 실시간성을 위해 두 값을 무리하게 합산(조회용+증감용) 반환 방법
    - 단점 : 동기화 찰나의 조회수가 맞지 +- 오차가 발생하며, 울렁거림이 발생거릴 수 있음
  - 예전 값을 보여줘도 배치만을 통한 Update로 일관성을 유지하는 방법
    - 단점 : 예전 값을 한동안 볼 수 있음
  - Message Queue를 활용하여 진행
    - 단점 : 관리 포인트가 늘어남
#### 스케줄링 업데이트 실패 시 데이터 유실 대응 (장애 복구 전략)
- 애플리케이션 레벨의 보상 트랜잭션 (try-catch)
  - DB 반영 실패 시, catch 블록에서 뺏어온 증감 값을 Redis에 **다시 increment하여 원복**
  - 특징: **구현이 단순**하고 가성비가 좋으나, **복구 시점에 Redis 자체 장애가 겹치면 유실 가능성이 존재**합니다
- 메시지 큐(Kafka)를 통한 완전한 비동기 집계
  - 스케줄러 방식을 버리고, **조회 증감 이벤트를 Kafka 큐에 던져 비동기로 소비** DB 반영 실패 시 DLQ로 메시지를 보내 영구 보관 후 재처리
  - 특징 : **데이터 유실을 0에 가깝게** 막아주지만, 인프라 관리 비용과 **시스템 복잡도가 크게 증가** 

## 좌석 점유 [NX 활용]
> NX = Not eXists = 키가 없을 때만 저장
> - 실무에서는 반드시 SET NX EX 를 같이 사용 ( TTL 없는 키가 영구 잔존하는 위험 방지)
> - Fail-Closed 전력 사용
>   - Redis 장애 → 락 획득 성공으로 처리 -> 최악의 경우: 동시 요청 모두 락 획득 → 중복 점유 → ❌ 결제 충돌

### Redis Use Flow
```text
[1단계] 지정 콘서트 좌석 목록 조회
  GET /concerts/1/seats
  └─ 전체 좌석 + 상태(AVAILABLE/HELD/SOLD) 반환

[2단계] 좌석 선택 → 임시 점유 (TTL)
  POST /seats/1/lock
  Body: { "userId": 1 }
  └─ 성공 (200): 결제 페이지로 이동
  └─ 실패 (409): "이미 점유된 좌석입니다" 안내

[3단계] 결제 진행
  POST /bookings (미구현 - 5단계)
  └─ 결제 완료 → SeatStatus SOLD
  └─ 락 해제

[4단계] 이탈 / 취소
  DELETE /seats/1/lock
  Body: { "userId": 1 }
  └─ 락 해제 → 좌석 다시 AVAILABLE
```

### 단순 락 vs Redisson 분산 락 비교
| 비교 항목 | 단순 락 (SETNX) | Redisson 분산 락 |
| :--- | :--- | :--- |
| **구현 복잡도** | 낮음 | 높음 |
| **락 재진입 (Reentrancy)**| 불가 | 가능 |
| **락 연장 (TTL 만료 방지)**| 수동 (직접 별도 로직 구현 필요) | 자동 (WatchDog 기능 지원) |
| **네트워크 장애 대응** | 약함 (스핀 락으로 인한 레디스 부하 유발 가능) | 강함 (Pub/Sub 기반으로 부하가 적음) |
| **적합한 상황** | 단순 점유 (예: 좌석 임시 점유) | 결제, 재고 차감 등 크리티컬한 비즈니스 구간 |


## [Sorted Set]  실시간 랭킹

### Redis Use Flow
```text
[1단계] 콘서트 상세 조회 → 조회수 + 랭킹 자동 반영
  GET /concerts/1
  └─ 조회수 증분: INCR concert:1:views:delta
  └─ 랭킹 최초 진입: ZSCORE null → DB views로 ZADD 초기화
  └─ 랭킹 점수 증가: ZINCRBY concert:ranking 1 "1"

[2단계] 인기 콘서트 랭킹 조회
  GET /concerts/ranking?size=10
  └─ ZRANGE concert:ranking 0 9 REV WITHSCORES
  └─ 응답: [ { concertId, score, rank }, ... ]

[3단계] 랭킹 → 콘서트 상세 일괄 조회 (4-3-3에서 구현)
  랭킹 상위 ID 목록 추출
  └─ MGET concert:1, concert:2, concert:3 ...
  └─ 콘서트 상세 정보 일괄 반환

[4단계] 조회수 DB 동기화 (배치, 10분 주기)
  @Scheduled fixedDelay=10분
  └─ GETSET concert:{id}:views:delta → "0"
  └─ delta > 0 → DB views 컬럼 반영
  └─ 랭킹 score는 DB 동기화 없음 (Redis 단독 관리)
```

### Redis Use Flow - Kafka 활용
> 점수 기반
```text
[ 점수 갱신 흐름 ]

유저 점수 획득
        ↓
┌─────────────────────────────────────────┐
│  Application Server                      │
│  1. Redis ZINCRBY game:ranking 500 "user:1" │  ← 즉시 반영
│  2. Kafka 이벤트 발행 (score-events)    │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Kafka                                   │
│  topic: score-events                     │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Kafka Consumer                          │
│  500개씩 배치 수신                      │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  MySQL                                   │
│  Bulk Update                            │
└─────────────────────────────────────────┘


[ 랭킹 조회 흐름 ]

유저 랭킹 조회 요청
        ↓
Redis ZREVRANGE game:ranking 0 9 (즉시 반환)


[ 동기화 흐름 - 매일 새벽 3시 ]

Scheduler 실행
        ↓
┌─────────────────────────────────────────┐
│  MySQL 전체 점수 조회                   │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  Redis 임시 Key에 적재                   │
│  ZADD game:ranking:new ...              │
└─────────────────────────────────────────┘
        ↓
┌─────────────────────────────────────────┐
│  RENAME으로 원자적 교체                  │
│  RENAME game:ranking:new game:ranking   │  ← 무중단
└─────────────────────────────────────────┘
```


## 콘서트 랭킹 목록 [MGET 활용]

### 풀려는 문제 사항
> 랭킹 조회 시 Reids에 저장된 랭킹 정보에는 상세 정보가 저장되어 있지 않음
> - 👎 랭킹 ID마다 GET 1회씩 Redis에 조회 하여 값을 채워 넣는 방식은 좋지 못함
```text
GET /concerts/ranking?size=10

현재 응답 (Reids에 저장된 ZSort 값):
[ { concertId: 1, score: 150, rank: 1 },
  { concertId: 3, score: 120, rank: 2 } ]

원하는 응답:
[ { concertId: 1, title: "IU 콘서트", artist: "IU", score: 150, rank: 1 },
  { concertId: 3, title: "BTS 월드투어", artist: "BTS", score: 120, rank: 2 } ]
```

### Redis Use Flow
```text
GET /concerts/ranking?size=10

[1단계] 콘서트 랭킹 조회 → 조회 목록 내 콘서트 ID들을 모아 캐싱된 콘서트 상세정보를 불러옴

[2단계] Mget을 사용해서 가져온 콘서트 정보를 기반으로 응답 데이터 생성
  └─ 케시 Hit 일 경우 DTO 변환 저장
  └─ 케시 Miss 일 캐싱 저장 후 DTO 반환
  └─ 존재하지 않는 콘서트 상세 정보일 경우 랭킹 목록에서 제외

응답:
[ { concertId: 1, title: "IU 콘서트", artist: "IU", score: 150, rank: 1 },
  { concertId: 3, title: "BTS 월드투어", artist: "BTS", score: 120, rank: 2 } ]
```

## 중복 조회 확인 [SET 활용]
> 중복이 불가능하다는 Set의 구조를 사용하여 조회 수 증감 방지
> userId의 경우 실개발 시에는 security-context를 사용하여 처리
### Redis Use Flow
```text
GET /concerts/{id}?userId=1

일별 중복 조회 체크 → SADD로 체크 + 등록 동시 처리 
  └─ 사용 Key 형식 "concert:{concertId}:viewers:{yyyyMMdd}" 저장되는 valued에는 사용자 식별ID 저장
  └─ SADD 반환값 0 (이미 존재) → 조회수/랭킹 증가 스킵, 콘서트 상세만 반환
  └─ SADD 반환값 1 (신규 등록) → EXPIREAT 당일 자정 만료 설정 후 다음 단계
```

## [SET] 게시글 좋아요 
> TTL 설정은 필수
### Redis Use Flow
```text
유저 클릭
    ↓
┌─────────────────────────────────────────────────────────────────────┐
│  분기 처리                                                           │
│  좋아요 클릭 → SADD post:1:likes "user:1"                          │
│  싫어요 클릭 → SREM post:1:likes "user:1"                          │
└─────────────────────────────────────────────────────────────────────┘
         ↓                                          ↓
┌─────────────────────┐                  ┌─────────────────────┐
│  Kafka               │                  │  Kafka               │
│  topic: like-events  │                  │  topic: like-events  │
│  (INSERT 이벤트)     │                  │  (DELETE 이벤트)     │
└─────────────────────┘                  └─────────────────────┘
         ↓                                          ↓
┌─────────────────────┐                  ┌─────────────────────┐
│  Kafka Consumer      │                  │  Kafka Consumer      │
│  500개씩 배치 수신   │                  │  500개씩 배치 수신   │
└─────────────────────┘                  └─────────────────────┘
         ↓                                          ↓
┌─────────────────────┐                  ┌─────────────────────┐
│  MySQL               │                  │  MySQL               │
│  Bulk Insert         │                  │  Bulk Delete         │
└─────────────────────┘                  └─────────────────────┘
```



## 예매 대기열 (Virtual Room) [Sorted Set 활용]

### 유저 흐름
> `waiting:concert:{id}:entered` 와  `waiting:concert:{id}` 를 2개를 관리하는 이유는 대기열 관리를 위해서 이다.
> - 입장 완료 유저가 대기열에 재진입하는 버그 발생을 방지하기 위함
> - 요약
>   - 유저 API ([1], [2]) : entered Set을 읽기만 함 (SISMEMBER) 
>   - 스케줄러([3]) : entered Set에 쓰기 함 (SADD)
```text
═══════════════════════════════════════════════════════════
[1] 대기열 진입 (멱등 — 몇 번 호출해도 안전)
═══════════════════════════════════════════════════════════

POST /concerts/{id}/waiting?userId=1   [ 콘서트 좌석 예매 가능 여부 확인 ]
        ↓
  ① SISMEMBER waiting:concert:{id}:entered "{userId}"
        ├── 응답 값 : 1 (입장 가능)
        │   → 토큰 조회 후 반환
        │   → 200 { status: ENTERED, token: "abc-123" }
        │   → 클라이언트: 좌석 선택 페이지[4]로 이동
        │
        └── 응답 값 : 0 (입장 불가 대기 필요) → ② 비즈니스 로직 진행
        ↓
  ② ZADD NX waiting:concert:{id} {timestamp} "{userId}" [ 대기열 등록 ]
        ├── 응답 값 : 1 (신규 진입)
        │   → ZRANK로 순번 조회
        │   → 201 { position: 3842, status: WAITING }
        │   → 클라이언트: 대기 화면으로 이동
        │
        └── 응답 값 : 0 (이미 대기 중이기에 현 위치 반환)
            → ZRANK로 현재 순번 조회
            → 200 { position: 1500, status: WAITING }
            → 클라이언트: 대기 화면으로 이동


═══════════════════════════════════════════════════════════
[2] 순번 폴링 (프론트가 3~5초 간격 반복 호출)
═══════════════════════════════════════════════════════════

GET /concerts/{id}/waiting/position?userId=1        [폴링 진행]
        ↓
  ① SISMEMBER waiting:concert:{id}:entered "{userId}"
        ├── 응답 값 : 1 (입장 완료)
        │   → 토큰 조회 후 반환
        │   → 200 { status: ENTERED, token: "abc-123" }
        │   → 클라이언트: 좌석 선택 페이지로 이동
        │
        └── 응답 값 : 0 → ② 단계 비즈니스 로직 진행
        ↓
  ② ZRANK waiting:concert:{id} "{userId}"
        ├── not null
        │   → 200 { position: 842, status: WAITING }
        │   → 클라이언트: "앞에 842명 남았습니다"
        │
        └── null (대기열에 없음)
            → 200 { status: NOT_IN_QUEUE }
            → 클라이언트: 진입 페이지로 이동


═══════════════════════════════════════════════════════════
[3] 입장 처리 (서버 측 — 스케줄러)
═══════════════════════════════════════════════════════════

@Scheduled 3초 간격
        ↓
  ① 현재 입장 인원 확인
     SCARD waiting:concert:{id}:entered
        ↓
  ② 빈 자리 계산
     입장 가능 수 = 최대 허용 인원 - 현재 입장 인원
        ↓
  ③ 빈 자리 > 0 일 때만 입장 처리
     ZPOPMIN waiting:concert:{id} count={빈 자리 수}
        ↓
  ④ 각 유저에 대해:
     SADD waiting:concert:{id}:entered "{userId}"
     SET waiting:concert:{id}:token:{userId} "{uuid}" EX 300
        ↓
  ⑤ 빈 자리 = 0 → 스킵


═══════════════════════════════════════════════════════════
[4] 입장 후 — 좌석 선택 + 점유 (기존 로직)
═══════════════════════════════════════════════════════════

GET /concerts/{id}/seats
Header: X-Entry-Token: abc-123
        ↓
  토큰 검증
    └─ GET waiting:concert:{id}:token:{userId}
        ├── 일치 → 좌석 목록 반환
        └── 불일치 or 없음 → 403 "입장 권한 없음"
        ↓
POST /seats/{id}/lock  (기존 SET NX EX 그대로)
```
