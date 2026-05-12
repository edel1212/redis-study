# TTL 전략

## 세 가지의 균형
> TTL = f(변경 빈도}, {무효화 가능성}, {허용 지연 시간})
### **변경 빈도** : "원본 데이터가 얼마나 자주 바뀌는가?"
- 데이터가 거의 변하지 않는다면(예: 우편번호, 국가 코드) TTL을 아주 길게 잡아도 안전하고 초 단위는 TTL를 마이크로 초까지 지정하는 등의 설정 필요
- `Result` : 변경이 잦을수록 TTL은 짧아져야 함
### **무효화 가능 여부** : "데이터가 변했을 때 캐시를 즉시 삭제(Evict)할 수 있는가?"
- DB 업데이트와 캐시 삭제를 **동시에 제어할 수 있다**면(명시적 Eviction), TTL을 굳이 짧게 가져갈 필요가 없음
- API 호출 결과처럼 **내가 데이터 변화를 감지할 수 없는 경우에는 무효화가 불가능**
- `Result` : 명시적 무효화가 가능하다면 TTL을 **길게**, 불가능하다면 **짧게**
### **허용 가능한 Stale(신선하지 않은) 시간** : "사용자가 얼마나 오래된 데이터를 봐도 참아줄 수 있는가?"
- 기술적인 문제보다는 **비즈니스 요구사항에 맞추는 것**이다.
- `Result` : **비즈니스에서 허용하는 오차 범위**가 좁을수록 TTL은 짧아져야 함

| TTL이 너무 짧으면 (Short TTL) | TTL이 너무 길면 (Long TTL) |
| :--- | :--- |
| 캐시 적중률(Hit Rate) ↓ | 오래된 데이터(Stale Data) 노출 위험 ↑ |
| DB 및 원본 서버 부하 가중 ↑ | 메모리 점유 및 자원 사용량 증가 ↑ |
| **Cache Stampede** 현상 발생 위험 ↑ | 무효화(Invalidation) 누락 시 데이터 불일치 영구화 |
| 캐시 레이어의 존재 의미(성능 이점) 퇴색 | 캐시 교체(Eviction) 정책에 대한 의존도 심화 |


## TTL Jitter 패턴
> cache stampede 현상을 막기 위한 패턴
> - 대규모/고부하 트래픽 제어가 필요한 곳에서 사용한다
> - 모든 캐시에 Jitter 적용 안 함. Stampede 위험이 실제로 있는 소수 캐시에만 적용

### cache stampede?
- 같은 만료시간으로 지정된 TTL이 동시에 해제 되면서 Cache Miss로 인해 부하가 발생하는 현상
```text
> 이슈 흐름:
시각 09:00:00 - 인기 공연 10개 캐시 일괄 세팅 (TTL 30분)
                ↓
                Redis: concert:1, concert:2, ..., concert:10 (TTL=1800)
                ↓
시각 09:30:00 - 모든 캐시 동시 만료 💥
                ↓
시각 09:30:00.001 - 사용자 1만 명이 동시에 조회 요청
                ↓
              모든 요청이 캐시 MISS → DB 직격탄 1만 건
                ↓
              DB CPU 100% → 응답 지연 → 타임아웃 → 장애 확산

> 이슈 발생:
만료 순간 (Stampede):
[Client 1] ┐
[Client 2] ├→ [Cache MISS 동시 발생] → [DB] 💥
[Client N] ┘                            ↓
                                   (1만 건 동시 쿼리)
```

### 참고사항
- `@Cacheable`은 너무 추상화되어 있어서 동적인 TTL(Jitter)을 주입하기 까다로움
- `RedisTemplate`을 직접 사용해 설정하는 방식을 많이 사용함

### 적용 방법

```java
public class TtlUtils {

    private TtlUtils() {}

    /**
     * 기준 TTL 에 ±ratio 범위의 jitter 적용
     * @param base   기준 TTL
     * @param ratio  jitter 비율 (0.1 = ±10%)
     */
    public static Duration jitter(Duration base, double ratio) {
        long baseSeconds = base.getSeconds();
        long jitterRange = (long) (baseSeconds * ratio);
        if (jitterRange <= 0) return base;

        long delta = ThreadLocalRandom.current()
                .nextLong(-jitterRange, jitterRange + 1);
        return Duration.ofSeconds(baseSeconds + delta);
    }
}

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl {
    // redis String을 Helper class로 사용
    private final RedisCacheHelper redisCacheHelper;
    
    // Jitter를 사용해서 TTL 설정
    public PostDto updatePostByJitter(Long id, RequestPost requestPost) {
        String key = "post:" + id;
        // [cache hit] 캐시 조회
        Optional<PostDto> optionalCached = redisCacheHelper.get(key, PostDto.class);
        if (optionalCached.isPresent()) {
            PostDto cached = optionalCached.get();
            log.info("cache hit : {}", cached);
            return cached;
        } // if

        log.info("cache miss read DB");
        // [cache miss] DB 조회
        PostDto postDto = postRepository.findById(id)
                .map(PostDto::from)
                .orElseThrow(() -> new RuntimeException("저장된 값을 찾을 수 없습니다."));

        log.info("write cache");
        Duration ttlByJitter = TtlUtils.jitter(Duration.ofMinutes(10), .2);
        redisCacheHelper.set(key, postDto, ttlByJitter);

        return postDto;
    }
}
```

## 동적 TTL
> 필수❌, Hot Key 또는 트래픽 편차가 클 경우 적용을 고려하자 구현 시 비용 및 구조가 복잡해짐

### 단계별 TTL 적용
```text
┌─ 1단계: 모든 캐시 동일 TTL ─────────────────┐
│  (안티패턴)                                  │
│  → 비효율, Stampede 위험                     │
└──────────────────────────────────────────────┘
              ↓ 진화

┌─ 2단계: 캐시 종류별 TTL 분리 (정적) ⭐ ─────┐
│  (실무 80% 가 여기서 끝남)                   │
│  → 회원=10분, 메뉴=1시간, 토큰=만료시간       │
│  → 3-4 ① 에서 다룬 내용                     │
└──────────────────────────────────────────────┘
              ↓ ✅ 더 정교해질 필요가 있을 때만

┌─ 3단계: 동일 캐시 내에서도 동적 TTL ⭐ ────┐
│  (Hot Key, 이커머스, 콘텐츠 서비스)          │
│  → 같은 "concert" 캐시여도 인기도별 차등       │
│  → 3-4 ③ 에서 다루는 내용                  │
│                                              │
│  결정 기준 (자주 쓰는 것):                    │
│  - 인기도 (view count, like count)            │
│  - 데이터 타입 (메타 vs 가격 vs 재고)          │
│  - 시간대 (오픈 임박)                         │
│  - 사용자 등급                                │
└──────────────────────────────────────────────┘
```
### 동적 TTL 결정 기준 (예시)
- 트래픽량 기반
```text
조회수 / 좋아요 / 즐겨찾기 수 → TTL 차등

티켓 예매 예시:
- 핫 공연 (10만+ 조회): 2시간
- 인기 공연 (1만+):     30분
- 일반 공연:            5분
```
- 데이터 타입 기반
```text
같은 도메인 내에서도 데이터 종류별로 다르게:

공연 메타정보 (제목/설명):  1시간 (잘 안 바뀜)
공연 가격:               5분  (변경 가능성 ↑)
잔여 좌석 수:             캐시 X (실시간 필요)
공연자 정보:             24시간 (거의 안 바뀜)
```
- 시간대 기반
```text
오픈 시간이 다가올수록 TTL 짧게:

D-7 ~ D-2:    1시간 (변경 잦지 않음)
D-1:          10분  (정보 업데이트 가능성 ↑)
오픈 당일:     1분   (실시간 가까이)
오픈 후:      5분   (안정화)
```

### ⚠️ 실무 함정

### 함정 1. TTL이 너무 동적이면 디버깅이 어려워짐
- 해결 방법: TTL 결정 로직을 한 곳(CachePolicy)에 모으고, 로깅 필수.

### 함정 2. 인기도 측정 자체가 부하 (조회 자체가 부하)
- 해결 방법: 
  - 인기도는 MISS 시점에만 조회 (HIT 시는 불필요)
  - 인기도 자체를 로컬 캐시(Caffeine)에 짧게 캐싱

### 적용 Policy Component
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class DynamicCachePolicy {
    private final StringRedisTemplate stringRedisTemplate;

    // Jitter ratios
    private double jitterRatio = .2;

    // 인기도 기준 (조회수)
    private static final long POPULAR_THRESHOLD = 10_000L;
    private static final long HOT_THRESHOLD = 100_000L;

    // TTL 정책
    private static final Duration TTL_HOT      = Duration.ofHours(2);    // 핫 공연
    private static final Duration TTL_POPULAR  = Duration.ofMinutes(30); // 인기 공연
    private static final Duration TTL_NORMAL   = Duration.ofMinutes(5);  // 일반 공연

    /**
     * 공연 ID 기준으로 TTL 결정
     */
    public Duration resolveTtl(Long concertId) {
        long viewCount = getViewCount(concertId);

        Duration baseTtl;
        if (viewCount >= HOT_THRESHOLD) {
            baseTtl = TTL_HOT;
        } else if (viewCount >= POPULAR_THRESHOLD) {
            baseTtl = TTL_POPULAR;
        } else {
            baseTtl = TTL_NORMAL;
        }// if - else

        log.debug("Resolved TTL for concert={}, viewCount={}, ttl={}",
                concertId, viewCount, baseTtl);

        // ✅ Jitter 적용 (Stampede 방지 - ② 의 결과물)
        return TtlUtils.jitter(baseTtl, jitterRatio);
    }

    /**
     * Redis 에서 조회수 가져오기
     */
    private long getViewCount(Long concertId) {
        String key = "concert:view-count:" + concertId;
        String count = stringRedisTemplate.opsForValue().get(key);
        return count == null ? 0L : Long.parseLong(count);
    }
}
```

## 캐시 키 전략 (KeyGenerator)
> 실무에서는 KeyGenerator 는 거의 안 쓴다고 보면된다.
> - `@Cacheable(cacheNames= "foo", key = "#paramName")` <- 와 같이 SpEL 명시하는 것이 실무 바식
- 파라미터 개수가 늘거나 DTO내 필드가 늘어날 경우 정상적으로 Redis에서 **값을 찾을 수 없음**

### 문제점 요약
> SpEL 명시하지 않을 경우 Spring 내에서 자동으로 Key를 생성한다.
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class KeyGeneratorServiceImpl {

    private final PostRepository postRepository;

    // 👍 SpEL 명시 안전함
    @Cacheable(cacheNames = "post", key = "#sample.id")
    public PostDto getPostType1(RequestSample sample){
        log.info("read DB");
        PostEntity postEntity = postRepository.findById(sample.getId())
                .orElseThrow(()-> new RuntimeException("Not Found"));
        return PostDto.from(postEntity);
    }

    // 👎 KeyGenerator로 생성 
    // - redis-key : "yoo:post:SimpleKey [3, 34]"
    @Cacheable(cacheNames = "post")
    public PostDto getPostType2(Long id, Long testNum){
        log.info("id : {}, testNum : {}", id, testNum);
        PostEntity postEntity = postRepository.findById(id)
                .orElseThrow(()-> new RuntimeException("Not Found"));
        return PostDto.from(postEntity);
    }


    // 👎 KeyGenerator로 생성 
    // - redis-key : "yoo:post:RequestSample(id=3, title=\xec\x9d\xb4\xea\xb1\xb4 \xeb\xb6\x88\xea\xb0\x80\xeb\x8a\xa5, dummy=dum)"
    @Cacheable(cacheNames = "post")
    public PostDto getPostType3(RequestSample requestSample){
        log.info("requestSample : {}", requestSample);
        PostEntity postEntity = postRepository.findById(requestSample.getId())
                .orElseThrow(()-> new RuntimeException("Not Found"));
        return PostDto.from(postEntity);
    }
}
```