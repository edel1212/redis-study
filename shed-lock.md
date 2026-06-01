# ShedLock — 분산 스케줄러

## ShedLock이란?
> 여러 서버(인스턴스)가 분산되어 실행되는 환경에서 동일한 스케줄링 작업이 중복으로 실행되는 것을 방지하는 Java 라이브러리


## 문제 예시
```text
[서버 3대 배포 — WaitingEntryScheduler]

  서버 A: @Scheduled(3초) → ZPOPMIN 50명 입장
  서버 B: @Scheduled(3초) → ZPOPMIN 50명 입장
  서버 C: @Scheduled(3초) → ZPOPMIN 50명 입장
  → 한 번에 150명 입장 💥 (최대 50명이어야 함)
```

## 실무 해결 방식 (3가지)
- ShedLock 사용 방식
- 스캐줄링 전용 서버로 분리하는 방식
- Kafka + 배치 방식

### 방식1: ShedLock 방식
> Redis의 메모리상에 Lock 정보(Lock 이름, 점유 시간 등)를 Key-Value 형태로 직접 기록
> - 스케줄링 대상 Table에 컬럼을 추가할 필요가 없음
#### 동작 원리
```text
서버 A: ShedLock 락 획득 시도 → SET NX → 성공 → 스케줄러 실행
서버 B: ShedLock 락 획득 시도 → SET NX → 실패 → 스킵
서버 C: ShedLock 락 획득 시도 → SET NX → 실패 → 스킵

→ 1대에서만 실행
```

### 방식2: 전용 스케줄링 서버 분리
> 결과적으로 스케줄링 서버 또한 1대가 아니라면 **ShedLock** 도입 필요
```text
API 서버 3대 (스케줄러 없음)
스케줄러 서버 1대 (@Scheduled만 실행)

장점: API 서버 성능에 영향 없음
단점: 스케줄러 서버 죽으면 전체 중단
      → 2대 배포 시 결국 ShedLock 필요
```

### 방식3: Kafka + 배치
> 스케줄링을 하지 않고 Message Queue 에 메세지를 담아 처리하는 방식

```text
[스케줄러 — 현재]

  10:00  유저 A 조회 → INCR delta
  10:05  유저 B 조회 → INCR delta
  10:10  유저 C 조회 → INCR delta
  ...
  10:10:00  스케줄러를 통해 Update → GET/SET delta=10000 → DB UPDATE 1회


🔍 [Kafka + 배치]

  10:00  유저 A 조회 → Kafka 발행: { concertId: 1 }
  10:05  유저 B 조회 → Kafka 발행: { concertId: 1 }
  10:10  유저 C 조회 → Kafka 발행: { concertId: 1 }
       ↓
  Consumer가 수신 → 메모리에 누적 또는 배열형식으로 Message를 받아옴
       ↓
  5초마다 계산된 값 → DB UPDATE 1회 
       ↓
  offset commit → "여기까지 처리했음" / 문제가 생겼을 경우 commit ❌
```

## 최종 판단 기준
> "스케줄러 또는 Kafka" 가 아닌 "현재 규모에 뭐가 맞는가"를 기준으로 선택 필요

- 스케줄러 2개, API 와 스케줄링 통합 구조      : ShedLock
- 스케줄러 20개+, 무거운 작업이 많은 API 서버  : 전용 서버 + ShedLock
- 규모가 크며, 부하 분산이 필수                : Kafka + 배치 
