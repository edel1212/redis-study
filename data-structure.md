# 핵심 자료구조
> SCAN 0 TYPE {{type}} 명령어를 통해 자료구조 개수를 알 수 있다.

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