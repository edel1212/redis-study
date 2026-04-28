# 필수 정보 및 핵심 자료 구조
> SCAN 0 TYPE {{type}} 명령어를 통해 자료구조 개수를 알 수 있다.

## [필수] Key 네이밍 컨벤션
> Redis는 **단일 네임스페이스**이기에 모든 Key가 한곳에 관리되기에 **체계가 없으면 관리가 불가능**하다.
```text
처음부터 체계 없이 만들면 나중에 운영 / 디버깅 / 모니터링 시 큰 부담이 될 수 있다.
****************************************************
⭐️Result : 팀 차원에서 컨벤션 문서화 후 적용하는 것이 정석 
****************************************************
```

### 표준 네이밍 패턴
> {도메인}:{식별자}:{용도} [:추가정보]
- 콜론(:) 구분자
- 소문자 사용
- 영문만 사용
- 약어 사용 금지
- 계층 구조
```shell
# 유저 관련
user:1001:profile          # 유저 1001의 프로필
user:1001:permissions      # 유저 1001의 권한

# 게시글 관련
post:100:detail            # 게시글 100 상세
post:100:likes             # 게시글 100 좋아요 (Set)

# 랭킹 관련
ranking:game:total         # 게임 전체 랭킹
ranking:game:daily         # 게임 일간 랭킹

# 티켓팅 관련
ticket:1:waiting           # 1번 공연 대기열
ticket:1:entered:user:N    # 1번 공연 입장 권한
seat:A12:lock              # 좌석 A12 분산락

# 캐시 관련
cache:product:101          # 상품 101 캐시
cache:user:1001:profile    # 유저 1001 프로필 캐시

# Rate Limiting
ratelimit:api:user:1001    # 유저 1001 API 요청 제한
```

### 환경 / 서비스 분리 네이밍
- prefix를 작성하여 명확히 분리 환경 / 도메인 분리
```shell
# 환경 분리
prod:user:1001:profile
dev:user:1001:profile
stage:user:1001:profile

# 서비스 분리 (멀티 도메인)
shopping:user:1001:cart
booking:user:1001:reservation
chat:user:1001:messages
```

## [필수] TTL
- Key가 자동으로 **삭제되기까지의 남은 시간**
- Redis는 메모리 기반이므로 TTL로 자동 정리하지 않으면 메모리 폭발한다.

| 옵션 | 단위 | 설명 | 사용 예시 |
| :--- | :--- | :--- | :--- |
| **EX** | 초 (Seconds) | 만료 시간을 **초** 단위로 설정 | `SET user:1 "yjh" EX 30` <br>(30초 후 삭제) |
| **PX** | 밀리초 (ms) | 만료 시간을 **밀리초** 단위로 설정 | `SET user:1 "yjh" PX 30000` <br>(30초 후 삭제) |
| **EXAT** | 타임스탬프 (초) | 만료될 **특정 시각**을 초 단위로 지정 | `SET user:1 "yjh" EXAT 1714125000` |
| **PXAT** | 타임스탬프 (ms) | 만료될 **특정 시각**을 밀리초 단위로 지정 | `SET user:1 "yjh" PXAT 1714125000000` |
| **KEEPTTL** | - | 기존에 설정된 **TTL을 유지**하며 값만 갱신 | `SET user:1 "new_val" KEEPTTL` |

### TTL 관련 명령어
- `EXPIRE {{key}} 60` : 초 단위 TTL 설정 
- `PEXPIRE {{key}} 6000` : 밀리세컨드 단위 TTL 설정 
- `EXPIREAT {{key}} {{timesteamp}}` : 지정 시간 까지 TTL 설 (초 단위 타임스템) 
- `TTL {{key}}` : 지정 키 TTL 조회 (초)
  - `100   # 100초 남음`
  - `-1    # TTL 설정 안됨 (영구 보존)`
  - `-2    # Key가 존재하지 않음`
- `PTTL {{key}}` : 지정 키 TTL 조회 (밀리세컨)
- `PERSIST {{key}}` : TTL 지정 제거 (영구 보존)

### TTL 예시
```shell
# 1. 생성과 동시에 TTL (String만 가능 - ✅ 다른 자료구조에서는 해당 방식 사용 불가)
SET name "유정호" EX 60

# 2. 생성 후 별도로 TTL (모든 자료구조)
ZADD ranking 100 "user:1"
# 'ranking' 라는 sorted set 자료구조에 TTL 설정
EXPIRE ranking 60

# 3. 특정 시각에 만료
EXPIREAT ticket:1:waiting 1700100000   # 공연 종료 시각

# 4. 기존 TTL 유지하며 **값만 변경**
SET name "김철수" KEEPTTL
```

## SET NX / XX 옵션
> 명령어 마지막에 작성한다.
- **NX** : 저장하려는 key가 없을 때만 SET을 진행한다.
  - `SET age 20 SE 3000 NX`
- **XX** : 저장하려는 key가 있을 때만 SET으로 덮어 씌운다.
  - `SET age 15 SE 3000 XX`



---

## String
> 가장 기본이 되는 자료구조로 텍스트 뿐만아니라  숫자, JSON, 바이너리 데이터까지 저장 함

### 저장/조회/삭제
```shell
# SET {{key}} {{value}}
SET name "흑곰"
# GET {{key}} {{value}}
GET name

SET age 20
GET age

DEL name
GET name # nil (없음)
```## String

### String 사용 시 주의사항
```shell
# ❌ 객체를 그냥 저장하면 문자열로 저장됨
SET user "[object Object]"

# ✅ JSON으로 **직렬화**해서 저장
SET user:1 '{"id":1, "name":"유정호"}'
```

### 다수 저장/조회
- 다수 저장 시 `TTL` 설정은 불가능하다
  - `EXPIRE` 명령어를 통해 따로 지정 필요
```shell
MSET name "흑곰" age 30 city "seoul"
MGET name age city
```

### 숫자 증감 명령어
> 문자열 사용시 ERR value is not an integer 발생
```shell
SET count 0

# 1씩 증감
# INCR {{key}}
INCR count # 1
INCR count # 2

# 지정값 증감
# INCRBY {{key}} {{value}}
INCRBY count 5 # 5증가

# 1씩 감소
# DECR {{key}}
DECR count

# 지정값 증감
# DECRBY {{key}} {{value}}
DECRBY count 5 # 5감소
```

## List
> 순서가 있는 문자열 목록이며, 앞(Left) 또는 뒤(Right) 양방향으로 데이터를 추가/삭제 가능

```text
HEAD                    TAIL
 ↓                       ↓
[A] - [B] - [C] - [D] - [E]
 ↑                       ↑
LPUSH                  RPUSH
```

### 배열 데이터 추가
```shell
# 오른쪽에 추가 - Right Push
# RPUSH {{list}} {{member}}
RPUSH mylist "A"

# 왼쪽에 추가 - Left Push
# LPUSH {{list}} {{member}}
LPUSH mylist "Z"
```

### 배열 조회
> 조회 범위 파라미터 (0 = 처음, -1 = 끝) 
```shell
# 전체 조회 (0 = 처음, -1 = 끝)
# LRANGE {{key}} {{start}} {{end}}
LRANGE mylist 0 -1    # Z A B C

# 특정 범위 조회
LRANGE mylist 0 1     # Z A

# 특정 인덱스 조회
LINDEX mylist 0 0       # Z
LRANGE RL -2 -1   # 마지막 2개

# 길이 조회
LLEN mylist           # 4
```

### 배열 데이터 삭제
```shell
# 왼쪽에서 꺼내기 (꺼내면서 삭제)
LPOP mylist    # Z → 결과: [A, B, C]

# 오른쪽에서 꺼내기
RPOP mylist    # C → 결과: [A, B]
```

## Set
> 중복을 허용하지 않는 집합
> SET자료 구조를 만들고 하위에 value를 넣는 개념이다.
> - TTL 설정은 Key 단위가 아닌 SET 단위로 설정이 가능하다.
- 같은 중복을 허용하지 않는 자료 구조지만 개수를 추정하는 ㅏ`HyperLogLog`도 있다.
  - 중복 없는 데이터 개수(Cardinality)를 **추정하는 자료구조**

###  추가 / 조회 / 삭제
```shell
# 추가
# SADD {{set}} {{member}}
SADD fruits "apple"
SADD fruits "banana"
SADD fruits "apple"    # 중복 → 무시됨

# SET 안에 요소 전체 조회
SMEMBERS fruits        # apple banana

# SET안에 개수 조회
SCARD fruits           # 2

# 특정 값 존재 여부
SISMEMBER fruits "apple"   # 1 (있음)
SISMEMBER fruits "grape"   # 0 (없음)

# TTL 설정 ( SET 단위 지정만 가능 ) - 상대 시간
# - 명령어를 실행하는 현재 시점이 기준 적용
EXPIRE fruits 3600

# TTL 설정 ( SET 단위 지정만 가능 ) - 절대 시간
# - 현재 시간과 관계없이 미래의 특정 지점이 기준
EXPIREAT fruits 1777561200

# TTL 확인
TTL fruits

# 삭제
SREM fruits "apple"
```
###  집합 연산
```shell
SADD set1 "A" "B" "C"
SADD set2 "B" "C" "D"

# 교집합 (공통 요소)
SINTER set1 set2      # B C

# 합집합 (전체 요소)
SUNION set1 set2      # A B C D

# 차집합 (set1 에만 있는 요소)
SDIFF set1 set2       # A
```

## Sorted Set
> score(점수)를 기반으로 자동 정렬되는 Set | 중복 허용 ❌ | 순위/정렬이 필요한 곳에 최적화
> 동일한 SET에 같은 값이지만 score를 다르게 넣으면 덮어 씌워진다.

### 데이터 추가 / 조회 / 삭제
```shell
# ZADD {{Sorted Set}} {{score}} {{member}}  
ZADD ranking 100 "유정호"
ZADD ranking 200 "김철수"
ZADD ranking 150 "이영희"

# 낮은 점수 순 조회 (0 = 1위, -1 = 끝)
ZRANGE ranking 0 -1

# 높은 점수 순 조회 (실시간 랭킹에 사용)
ZREVRANGE ranking 0 -1

# 점수와 함께 조회
ZREVRANGE ranking 0 -1 WITHSCORES

# 특정 멤버 점수 조회
# ZSCORE {{Sorted Set}} {{member}}
ZSCORE ranking "유정호"       # 100

# 특정 멤버 순위 조회 (0부터 시작)
ZREVRANK ranking "1등"     # 0 (1위)

# 특정 멤버 삭제
# ZREM {{Sorted Set}} {{member}}
ZREM ranking "유정호"

# 특정 순위 범위 삭제
# ZREMRANGEBYRANK {{Sorted Set}} {{start rank}} {{end rank}} 
ZREMRANGEBYRANK ranking 0 2   # 1~3위 삭제
```

### 점수 기존 값 +, - 변경
> 기존의 값을 덮어씌우려면 ZADD를 사용하면된다.
```shell
# 기존 값에서 점수 증가
# ZINCRBY {{Sorted Set}} {{score}} {{member}}
ZINCRBY ranking 50 "유정호"   # 100 → 150

# 기존 값에서  점수 감소
ZINCRBY ranking -30 "이영희"  # 150 → 120
```

### 랭킹 관련
```shell
# 가장 낮은 score 멤버 꺼내기 (꺼내면서 삭제)
ZPOPMIN ranking          # 1개 꺼냄
ZPOPMIN ranking 3        # 3개 꺼냄

# 가장 높은 score 멤버 꺼내기
ZPOPMAX ranking
ZPOPMAX ranking 3

# 전체 멤버 개수
ZCARD ranking

# score 범위로 조회 (예: 100~200점 사이)
ZRANGEBYSCORE ranking 100 200

# score 범위 내 멤버 개수
ZCOUNT ranking 100 200

# 무한대 표현
ZRANGEBYSCORE ranking -inf +inf       # 전체
ZRANGEBYSCORE ranking 100 +inf        # 100점 이상
ZRANGEBYSCORE ranking -inf 200        # 200점 이하
```

## Hash
> 필드(field)와 값(value)의 쌍을 저장하는 자료구조입니다. 객체(Object)를 저장할 때 적합

### String 저장과 비교
- JSON 구조 자체를 HASH 형식으로 저장하여 **값 수정에 용의**하다.
```text
일반 String 방식:
SET user:1 '{"name":"유정호", "age":30, "city":"incheon"}'
        ↓
필드 하나만 수정하려면 전체 JSON 다시 저장 ❌

Hash 방식:
HSET user:1 name "유정호" age 30 city "incheon"
        ↓
특정 필드만 수정 가능 ✅
HSET user:1 age 31  # age만 변경
```

### 데이터 추가 / 조회
```text
# 단일 필드 추가
HSET user:1 name "유정호"

# 여러 필드 한번에 추가
HSET user:1 name "유정호" age 30 city "incheon"

# 단일 필드 조회
HGET user:1 name           # "유정호"

# 여러 필드 조회
HMGET user:1 name age      # "유정호" 30

# 전체 필드 조회
HGETALL user:1

# 모든 필드명만 조회
HKEYS user:1               # name age city

# 모든 값만 조회
HVALS user:1               # "유정호" 30 "incheon"

# 필드 개수
HLEN user:1                # 3
```

### 필드 존재 여부 확인 / 삭제
```text
# 필드 존재 여부
HEXISTS user:1 name        # 1 (있음)
HEXISTS user:1 phone       # 0 (없음)

# 특정 필드만 삭제
HDEL user:1 city

# 여러 필드 동시 삭제
HDEL user:1 age phone
```
### 기존 값 대비 숫자 증감
- HASH에서는`INCR` **명령어 없음**
```text
# 필드 값 증가
HINCRBY user:1 age 1       # age 1 증가

# 필드 값 감소
HINCRBY user:1 age -5      # age 5 감소

# 실수 증감
HINCRBYFLOAT user:1 score 1.5
```