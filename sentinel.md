# Sentinel
- Master가 정해지고 그외 Slave(replica)는 **같은 데이터를 복제하여 저장**한다.
- Sentinel들은 Master는 감시하다. Master가 사망하면 투표를 통해 Slave(replica)중 Master를 선별한다.

## 해결하려는 문제
>  단일 Redis 상태에서 해당 서버가 죽으면 발생하는 문제
```text
Redis 단일 노드 다운 💥
   │
   ├─ 캐시(RedisCacheHelper, RedisCacheManager) → 전부 DB 직접 조회 (cache stampede)
   ├─ 좌석 락(SET NX EX)           → 점유 모델 붕괴 → 좌석 중복 점유 위험
   ├─ Redisson RLock(seat-mutex) → 임계 구간 보호 사라짐
   ├─ 대기열(Sorted Set)           → 대기 순서·entered·token 전체 소실
   └─ 카운터/랭킹                   → 조회수·랭킹 집계 중단
```

### Sentinel이 필요 이유 - Replication만으로는 해결 할 수 없음
> 누군가 수동으로 Replica1 ~ N을 master로 승격 시켜줘야 함
> - 수동으로 할 해야는 일을 자동화 담당하는 것이 "Sentinel" 이다.
```text
Master 💥
Replica1, Replica2 → 데이터는 갖고 있지만 여전히 "읽기 전용 replica"
   → 누군가 수동으로 Replica1을 master로 승격(SLAVEOF NO ONE)
   → 모든 클라이언트 설정을 새 master 주소로 변경
   → ☠️ 그동안 서비스 다운 (사람이 깨서 손으로 조치할 때까지) 💥
```

## Sentinel이 하는 4가지 일
> 클라이언트(서버)는 직접 Redis 연결이 아닌 Sentinel에 연결
> - "현재 master 주소"를 받아 접속, failover로 master가 바뀌어도, Sentinel에게 새 주소를 받아 자동 재연결 진행
> - 
- **Monitoring** : master·replica가 살아있는지 지속 감시
- **Notification** : 장애 발생 시 알림(스크립트/API)
- **Automatic Failover** : master가 죽으면 replica 하나를 자동으로 master로 승격
- **Configuration Provider** : 클라이언트(서버)가 "지금 master 누구야?"에 대한 응답

```text
                    ┌─────────────┐
                    │  Sentinel 1 │
   감시·합의  ────────┤  Sentinel 2 │   (3개 = 홀수 권장)
                    │  Sentinel 3 │
                    └──────┬──────┘
                           │ 감시
              ┌────────────┼────────────┐
              ▼            ▼             ▼
        ┌──────────┐  ┌──────────┐ ┌──────────┐
        │  Master  │─▶│ Replica1 │ │ Replica2 │
        └──────────┘  └──────────┘ └──────────┘
              ▲ 복제 ────────┘             │
              └───────────────────────────┘

  클라이언트(Spring) ──연결──> Sentinel들 ──"master는 X"──> Master에 접속
```
### Sentinel이 3개(홀수) 이유
> failover ? 장애가 발생했을 때, 예비 시스템으로 자동 전환되어 서비스를 중단 없이 계속 제공하는 기능
- Sentinel들의 **합의(quorum)로** "master가 진짜 죽었나"를 판단 후 **누가 failover를 주도**할지 리더를 **과반수 투표**로 뽑기 때문
  - 짝수일 경우 **split-brain(2:2로 갈림)** 위험이 커지고, **1개일 경우 그 Sentinel 자체가 SPOF** 
  - 홀수(보통 3) 가 과반수 합의의 표준

### Failover 흐름 
- **SDOWN** : 한개의 Sentinel만의 판단이다.
  - failover **시작하지 않음**, 네트워크 일시 단절로 한 Sentinel만 master를 못 볼 수도 있기 때문이다.
- **ODOWN** : Sentinel들의 합의(quorum)를 통해 도달 - 오탐에 대한 불필요한 failover를 방지함
  - failover 시작
```text
① Master 응답 없음
      │
      ▼
② 한 Sentinel가 "replica가 문제가 있다" 판단 → SDOWN (주관적 다운)
      │
      ▼
③ quorum 수만큼의 Sentinel이 동의 → ODOWN (객관적 다운) ★진짜 다운 확정
      │
      ▼
④ Sentinel들이 failover 주도할 리더 1명 선출 (과반수 투표)
      │
      ▼
⑤ 리더가 replica 중 새 master 선택
      (기준: replica-priority → 복제 offset 최신 → runid 순)
      │
      ▼
⑥ 선택된 replica를 master로 승격 + 나머지 replica를 새 master에 재연결
      │
      ▼
⑦ 클라이언트가 Sentinel에 재 질의 → 새 master 주소 받아 자동 재연결 ✅
```

## Sentinel 와 Cluster 비교
> Sentinel ≠ Cluster 서로 다른 개념

| 구분 | Redis Sentinel                       | Redis Cluster            |
| --- |--------------------------------------|--------------------------|
| **목적** | 고가용성(HA), 자동 Failover                | HA + 데이터 샤딩(수평 분산)       |
| **데이터** | Master **한 곳에 전부 저장**                | 16,384 슬롯으로 여러 노드에 분산    |
| **확장** | 수직 확장(메모리 키우기) 위주                    | 수평 확장 가능                 |
| **적합** | 데이터가 한 노드 메모리에 관리해도 괜찮아서, **HA만 필요할 경우** | 데이터가 너무 커서 **분산 필요할 경우** |
| **운영 난이도** | 중간                                   | 높음                       |


## Sentinel 구조
```text
master    : 6379  (쓰기)
replica-1 : 6380  (master 복제)
replica-2 : 6381  (master 복제)
sentinel-1: 26379 ┐
sentinel-2: 26380 ├ quorum=2 로 master 감시
sentinel-3: 26381 ┘
```

## Sentinel Docker
> 참고 : Redis Sentinel은 실행되면서 마스터의 상태가 바뀌거나 다른 센티널을 발견하면 자신의 sentinel.conf 파일에 설정을 **실시간으로 재작성(Rewrite)** 함
