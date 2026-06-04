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

### Sentinel Docker Compose
> 참고 : Redis Sentinel은 실행되면서 마스터의 상태가 바뀌거나 다른 센티널을 발견하면 자신의 sentinel.conf 파일에 설정을 **실시간으로 재작성(Rewrite)** 함

```yaml
services:
  # ==========================================
  # 1. Redis Master 노드
  # ==========================================
  redis-master:
    image: redis:7-alpine
    container_name: redis-master
    # --replica-announce-ip: 복제본이나 센티널에게 자신을 홍보할 때 사용할 IP를 지정합니다.
    # 외부 호스트(맥)를 통해 포트 포워딩으로 접근해야 하므로 host.docker.internal을 알립니다.
    command: redis-server /etc/redis/master.conf
    volumes:
      - ./conf:/etc/redis
    ports:
      - "6379:6379"

  # ==========================================
  # 2. Redis Replica (복제본) 노드들
  # ==========================================
  redis-replica-1:
    image: redis:7-alpine
    container_name: redis-replica-1
    command: redis-server /etc/redis/replica-1.conf
    volumes:
      - ./conf:/etc/redis
    ports:
      - "6380:6380"
    depends_on:
      - redis-master

  redis-replica-2:
    image: redis:7-alpine
    container_name: redis-replica-2
    command: redis-server /etc/redis/replica-2.conf
    volumes:
      - ./conf:/etc/redis
    ports:
      - "6381:6381"
    depends_on:
      - redis-master

  # ==========================================
  # 3. Redis Sentinel (감시자) 노드들
  # ==========================================
  # 센티널은 설정 파일(sentinel.conf)이 필수
  # ==========================================

  sentinel-1:
    image: redis:7-alpine
    container_name: sentinel-1
    command: redis-sentinel /etc/redis/sentinel-1.conf
    volumes:
      - ./conf:/etc/redis
    ports:
      - "26379:26379"
    depends_on:
      - redis-master

  sentinel-2:
    image: redis:7-alpine
    container_name: sentinel-2
    command: redis-sentinel /etc/redis/sentinel-2.conf
    volumes:
      - ./conf:/etc/redis
    ports:
      - "26380:26380"
    depends_on:
      - redis-master

  sentinel-3:
    image: redis:7-alpine
    container_name: sentinel-3
    command: redis-sentinel /etc/redis/sentinel-3.conf
    volumes:
      - ./conf:/etc/redis
    ports:
      - "26381:26381"
    depends_on:
      - redis-master
```
### 연결 검증
- Sentinel이 master-redis 인식 확인
  - `docker exec sentinel-1 redis-cli -p 26379 sentinel master cache-redis` 
    - `name, flags master, port`가 지정된 master redis로 식별되는지 확인
- replica 개수 확인
  - `docker exec sentinel-1 redis-cli -p 26379 sentinel replicas cache-redis`
    - 초기 지정한 6380, 6381 두 개가 목록에 나오면 정상
- 복제가 실제로 동작 확인
```shell
docker exec redis-master redis-cli -p 6379 set foo bar
docker exec redis-replica-1 redis-cli -p 6380 get foo
# response :  "bar"
```

## Failover 테스트

### STEP ① — 죽이기 전 현재 master redis 확인
- 요청 :`docker exec sentinel-1 redis-cli -p 26379 sentinel get-master-addr-by-name cache-redis`
- 응답 :
```text
host.docker.internal
6379
```

### STEP ② — master redis 종료 후 sentinel 로그 실시간 관찰 
- 요청 :
```shell
docker logs -f sentinel-1

docker stop redis-master
```
- 응답 :
```text
# +sdown master cache-redis {{IP}} 6379                   ← 5초 후: 주관적 다운 (6379인 master redis에 신호를 못받음)
# +odown master cache-redis {{IP}} #quorum 2/2       ← 2개 합의: 객관적 다운 확정
+try-failover master cache-redis {{IP}} 6379              ← failover 시도

# +vote-for-leader ...           ← 리더 sentinel 투표                                    

# +elected-leader master cache-redis {{IP}} 6379
# +failover-state-select-slave master cache-redis {{IP}} 6379
# +selected-slave slave IP:6380 {{IP}} 6380 @ cache-redis {{IP}} 6379 ← 어느 replica를 승격할지 선택

+switch-master cache-redis {{IP}} 6379 {{IP}} 6380  ← master 교체 완료
```
### STEP ③ — 새로운 master redis 확인
- 요청 :`docker exec sentinel-1 redis-cli -p 26379 sentinel get-master-addr-by-name cache-redis`
- 응답 :
```text
host.docker.internal
6380
```

## sentinel를 kill 시킨다면?
> sentinel중 하나만 잘 살아있다면 문제 없이 사용이 가능하다 HA(고가용성)
- SpringBoot 내 로그 (SentinelConnectionManager )
```text
sentinel: redis://127.0.0.1:26379 is down
sentinel: redis://127.0.0.1:26380 is down
sentinel: redis://127.0.0.1:26380 added
sentinel: redis://127.0.0.1:26381 is down
```

## ⭐ App 동작을 결정하는 건 "독립된 2가지 조건"
- master가 살아있을 경우 → sentinel 1개여도 App 동작 ✅ (조건 A만 보면 됨, B 불필요)
- master가 죽어있을 경우 → sentinel 과반이 있어야 함 - 새 master 생겨서 App 복구 ✅ / 1개뿐이면 APP 정상 동장 ❌
```text
조건 A — 데이터 경로:  클라이언트가 아는 현재 master가 살아있어야 함
                       (ReadMode.MASTER면 읽기·쓰기 모두 master로 감)

조건 B — 복구 능력:    master가 죽으면 → 새 master로 전환(failover)
                       → 이건 sentinel "과반(majority)"이 필요. 1개로는 불가
```

| 장애 발생 (죽인 것) | Redis 생존 | Sentinel 생존 | Failover 가능 여부 | App 동작 | 이유 및 상태 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 없음 (정상) | M + R + R | 3 | ✅ 가능 | ✅ 정상 | 정상 상태 |
| Sentinel 1개 | M + R + R | 2 | ✅ 가능 | ✅ 정상 | Sentinel 과반(2/3) 유지 중이며 Master가 살아있음. |
| Sentinel 2개 | M + R + R | 1 | ❌ **불가** | ✅ 정상 | Master가 살아있어 App(읽기/쓰기)은 동작함. 단, 지금 Master가 죽으면 Sentinel 과반 부족으로 복구 불가 (위태로운 상태). |
| Master | R + R | 3 | ✅ 가능 | ✅ (몇 초 후) | Sentinel 과반(3/3) 충족. Replica 중 1대가 새 Master로 승격됨. |
| Master + Sentinel 2개 | R + R | 1 | ❌ **불가** | ❌ 실패 | Master 사망 및 Sentinel 과반 부족(1/3). Failover가 불가능하여 Master가 없는 상태 지속 (쓰기 작업 실패). |
| Master + Replica 1개 | R | 3 | ✅ 가능 | ✅ (몇 초 후) | Sentinel 과반(3/3) 충족. 남은 Replica 1개가 새 Master로 승격됨. |
| Master 죽음 → 6381 승격 → 6381도 죽음 | 남은 R 1개 | 3 | ✅ 가능 | ✅ (몇 초 후) | Sentinel 과반이 유지되므로 남은 Replica가 다시 Master로 승격됨. (추가) 죽었던 기존 Master(old M)가 복구되면 새 Master의 Replica로 자동 편입됨. |