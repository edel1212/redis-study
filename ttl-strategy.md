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


