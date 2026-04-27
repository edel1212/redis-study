## maxmemory-policy (메모리 정책) 설정 옵션
> 메모리가 가득 찼을 경우 진행 방향 설정
| 정책 | 설명 | 추천 시나리오 |
| :--- | :--- | :--- |
| **noeviction** | 삭제 없이 에러 반환 | 데이터 보존이 필수일 때 |
| **allkeys-lru** | 전체 중 오래된 것 삭제 | **일반적인 캐시 서버** |
| **volatile-lru** | TTL 키 중 오래된 것 삭제 | DB 보조 캐시 용도 |
| **allkeys-lfu** | 전체 중 적게 쓴 것 삭제 | 사용 빈도가 중요할 때 |
| **volatile-ttl** | 만료 임박 키부터 삭제 | 만료 시점 관리가 중요할 때 |


## Redis 용도별 Persistence (RDB/AOF) 설정 가이드
> 💡 Redis에 없어서는 안 될 데이터를 저장하는 설계 자체가 잘못된 것이다.  
> - Redis는 보조 저장소이고 원본 데이터는 항상 RDB(MySQL 등)에 있어야 함  
> - 캐시 용도라면 Persistence를 꺼도 전혀 문제없는 구조가 맞음  

## 용도별 실무 설정

| 용도 | RDB | AOF | 이유 |
| :--- | :---: | :---: | :--- |
| **캐시 전용** | ❌ | ❌ | 데이터 유실 시 DB에서 다시 로드하면 됨 (성능 중시) |
| **세션 관리** | ✅ | ❌ | 어느 정도의 유실은 허용 가능 |
| **랭킹 / 카운터** | ✅ | ✅ | 데이터 유실 최소화가 필수적임 |
| **분산 락** | ❌ | ❌ | TTL 기반이라 재시작 시 Persistence 의미 없음 |

## RDB (Redis Database)
> 설치 시 기본 값으로 세팅되어 있다. 
- `config get save` 명령어를 통해 설정 정보를 받아 올 수 있음
  - ex) `{{지정시간}} {{변경 횟수}}` 에 맞으면 저장

## AOF (Append Only File)
> 모든 쓰기 명령어를 로그 파일에 순서대로 기록하는 방식

### Flow
```text
SET name yoo  → appendonly.aof 에 기록
SET age 20    → appendonly.aof 에 기록
DEL name      → appendonly.aof 에 기록
      ↓ 서버 재시작 시
명령어를 처음부터 순서대로 재실행하여 복구
```

### 저장 설정
```text
appendfsync always    # 매 명령어마다 저장 (가장 안전, 가장 느림)
appendfsync everysec  # 1초마다 저장 (권장 ✅)
appendfsync no        # OS가 알아서 저장 (가장 빠름, 가장 위험)
```

### 장/단점
- 👍장점 : 데이터 유실 최소화 (최대 1초치만 유실)
- 👎단점 : 
  - 파일 크기가 커지고 복구 속도가 느림
  - 서버 성능에 상대적으로 더 영향을 줌 