# Spring Boot With Redis

## Lettuce 
> Java용 Redis 클라이언트
> - 실제 Redis와 통신은 Lettuce가 담당 (Deault 값)
> - Spring Boot 2.0+ 부터 기본 클라이언트는 Lettuce // 이전에는 Jedis 를 사용했음

### Lettuce vs Jedis
- Jedis :
  - **동기**(Blocking) 방식
  - 멀티스레드 환경에서는 **커넥션 풀 필수**
  - 현 시점에서는 사용 ❌
- Lettuce :
  - 비동기(Non-Blocking) 방식 + 동기 방식 **모두 지원**
  - Netty 기반으로 동작
  - 하나의 커넥션으로 멀티스레드에서 동시 처리 가능
  - 👍모든 부분에서 이점이 많음

## `RedisTemplate<String, Object>` 설정
> `RedisTemplate<Object, Object>` 사용 시 prefix에 JDK 직렬화의 문제점 발생 (Default 값)
>  - ex) "\xac\xed\x00\x05t\x00\x03foo"

### 직렬화/역직렬화 흐름
```text
[ Java 객체 (예: UserDto) ]
     ↓ (직렬화)
[ GenericJackson2JsonRedisSerializer ]
     ↓ 타입 정보(@class)를 JSON에 자동 포함
[ {"@class":"com.example.dto.UserDto","name":"홍길동","age":30} ]
     ↓
[ Redis 저장 (bytes) ]
     ↓ (역직렬화)
[ GenericJackson2JsonRedisSerializer ]
     ↓ @class 읽어서 타입 자동 판별
[ 원래 Java 객체 (UserDto)로 복원 ]
```
### 설정 방법
- `RedisTemplate<String, Object>`의 경우 자동으로 bean에 등록되지 않으므로 수동 설정 필요
- `GenericJackson2JsonRedisSerializer`를 사용하여 `@class` 정보를 자동으로 JSON에 포함하여 저장
    - 역직렬화 시 저장된 `@class`정보를 읽어서 반환
    - 보안 및 확장성에는 좋지 못한 방식
```java
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory
    ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        // 설정이 간단하고 범용적이기에 GenericJackson2JsonRedisSerializer로 직렬화 설정을 한다.
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        // Key 직렬화 (String)
        // 최상위 Key
        template.setKeySerializer(stringSerializer);
        // Hash 내부 Field명
        template.setHashKeySerializer(stringSerializer);

        // Value 직렬화 (JSON)
        // 최상위 Key
        template.setValueSerializer(jsonSerializer);
        // Hash 내부 Field명
        template.setHashValueSerializer(jsonSerializer);

        return template;
    }
}
```

## RedisTemplate
> Redis 명령어를 직접 사용하는 방식 (Low-level API)
> - 👍 세밀한 제어 가능, 모든 자료구조 활용 가능

### RedisRepository 미사용 이유
> JPA와 같은 방식으로 객체 단위로 사용이 가능하여 편리하나 **유연성이 떨어짐** 
- TTL 잔존 데이터 이슈 : 설정으로 처리가 가능하긴 함
- Keyspace Event 의존
- 자료구조 제한

### RedisTemplate Ops 메서드 종류
| Ops 메서드 | 자료구조 | 설명 |
| :--- | :--- | :--- |
| `opsForValue()` | **String** | 가장 기본적인 키-값(Key-Value) 구조 |
| `opsForList()` | **List** | 순서가 있는 목록 (중복 허용, 양방향 삽입/삭제) |
| `opsForSet()` | **Set** | 순서가 없는 집합 (중복 불가) |
| `opsForZSet()` | **Sorted Set** | 점수(Score)에 따라 정렬된 집합 |
| `opsForHash()` | **Hash** | 필드와 값으로 구성된 Map 구조 |
| `opsForHyperLogLog()` | **HyperLogLog** | 매우 적은 메모리로 대규모 데이터의 중복 없는 개수 추정 |

### RedisTemplate Example
- TTL 설정 시에는 파라미터 추가 방식보단 `expire()`메서드를 활용하자
- `RedisTemplate<String, Object>`는 **Bean 설정 추가 필요**
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisTemplateUsingService {

    // 다양한 자료구조 redisTemplate
    // 🔍 RedisTemplate<String, Object>는 Bean에 자동 등록 되어 있지 않기에 @Link{@RedisConfig} 설정이 필수이다.
    // ops -> Operations 줄임말
    private final RedisTemplate<String, Object> redisTemplate;
    // string 방식으로만 value를 받음
    private final StringRedisTemplate stringTemplate;

    /**
     * 👍 Object로 값을 받지 않기 때문에 stringTemplate 가 좀 더 안정적으로 사용이 가능함
     *
     */
    public void redisMethod(){
      stringTemplate.opsForValue().set("K","V");
      stringTemplate.opsForList().leftPush("K","V");
      stringTemplate.opsForSet().add("K","V");
      stringTemplate.opsForZSet().add("K","V",200);
  
    }
    
    /**
     * Key 삭제
     * - DEL 명령어는 데이터 타입(String, List, Set 등)에 관계없이 '키' 자체를 제거하는 공통 명령어를 사용
     */

    // 단건 삭제
    public void deleteKey(String k){
        redisTemplate.delete(k);
    }

    // 다건 삭제
    public void deleteKeys(List<String> keys){
        redisTemplate.delete(keys);
    }

    /**
     * TTL 
     */
    
    // 초 기준 설정 (상대 시간)
    public void setExpire(String key, Long timeOut){
        redisTemplate.expire(key, Duration.ofSeconds(timeOut));
    }

    // 지정 날짜 기준 설정 (절대 시간)
    public void setExpireAt(String key, Date date){
        redisTemplate.expireAt(key, date);
    }

    // 지정 키 TTL 확인
    public Long getExpire(String key){
        return redisTemplate.getExpire(key);
    }

    
    /**
     * Key 자료구조
     */
    
    // 저장
    public void saveString(String k, String v){
        redisTemplate.opsForValue().set(k, v);
    }

    // ❌[비추천] 저장 - with TTL
    @Deprecated
    public void saveStringWithTTL(String k, String v, Long timeOut){
        redisTemplate.opsForValue().set(k, v, Duration.ofSeconds(timeOut));
    }

    // 값을 가져옴
    public String getString(String k){
        Object value = redisTemplate.opsForValue().get(k);
        return value == null ? "null" : value.toString();
    }

    /**
     * List 자료구조
     */

    // 저장
    public void saveList(String k, String v){
        // Left에 값 추가
        redisTemplate.opsForList().leftPush(k, v + "- add sub Fix : L");
        // Right에 값 추가
        redisTemplate.opsForList().rightPush(k, v + "- add sub Fix :R");
    }

    // 조회
    public List<Object> getList(String k){
        // Left에 값 추가
        int start = 0;
        int end = -1;
        return redisTemplate.opsForList().range(k, start, end);
    }

    // 추출 후 제거
    public Object getPop(String k){
        //return redisTemplate.opsForList().rightPop(k);
        return redisTemplate.opsForList().leftPop(k);
    }

    /**
     * Set 자료구조
     */

    // 저장
    public void saveSet(String k, String member){
        // value의 경우 다수의 파라미터 전달 가능 (v, v1, v2, v3 ...);
        redisTemplate.opsForSet().add(k,member);
    }

    // 🤷‍♀️[꼭 확인] 저장 - TTL
    // - TTL 설정을 파라미터로 주면 안된다 (맴버로 추가됨)
    public void saveSetWithTTL(String k, String member, Long timeOut){
        // value의 경우 다수의 파라미터 전달 가능 (member, member1, member2, member3 ...);
        redisTemplate.opsForSet().add(k, member);
        // 👍 키 전체에 만료 시간 설정 (이건 모든 자료구조 공통)
        redisTemplate.expire(k, Duration.ofSeconds(timeOut));
    }

    // 지정 key 맴버 조회
    public Set<Object> getSet(String k){
        return redisTemplate.opsForSet().members(k);
    }

    // 지정 key 맴버 개수
    public Long getSetSize(String k){
        return redisTemplate.opsForSet().size(k);
    }

    /**
     * Sorted Set 자료구조
     */

    // 저장
    public void saveSortedSet(String k, String member, long score){
        // 필요의 경우 TTL 처리 가능함
        // 💻 TTL의 경우 따로 expire(key, Duration) 설정을 해줘야함
        redisTemplate.opsForZSet().add(k ,member, score);
    }
    
    // Score 오름차순 조회
    public Set<Object> getSortedSetByAsc(String k){
        int start = 0;
        int end = -1;
        return redisTemplate.opsForZSet().range(k, start, end);
    }

    // Score 내림차순 조회
    public Set<Object> getSortedSetByDesc(String k){
        int start = 0;
        int end = -1;
        return redisTemplate.opsForZSet().reverseRange(k, start, end);
    }

    // 스코어가 가장 낮은 값 pop
    public Object getSortedSetMinPop(String k){
        // 🔍 popMin 값은 TypedTuple 이다.
        ZSetOperations.TypedTuple<Object> tuple = redisTemplate.opsForZSet().popMin(k);
        return (tuple != null) ? tuple.getValue() : null;
    }

    // 스코어가 가장 높은 값 pop
    public Object getSortedSetMaxPop(String k){
        ZSetOperations.TypedTuple<Object> tuple = redisTemplate.opsForZSet().popMax(k);
        return (tuple != null) ? tuple.getValue() : null;
    }

    /**
     * Hash 자료구조
     */

    // 저장
    public void saveHash(String k, String hashKey, String value){
        // 필요의 경우 TTL 처리 가능함
        // 💻 TTL의 경우 따로 expire(key, Duration) 설정을 해줘야함
        redisTemplate.opsForHash().put(k, hashKey, value);
    }

    // 조회
    public Object getHashValue(String k, String hashKey){
        return redisTemplate.opsForHash().get(k, hashKey);
    }

    // Hash Key 조회
    public Set<Object> getHashKeys(String k){
        return redisTemplate.opsForHash().keys(k);
    }

    // 해당 Hash(k)에 저장된 모든 필드의 Value들을 리스트로 반환
    public List<Object> getHashValues(String k){
        return redisTemplate.opsForHash().values(k);
    }
    
}
```

## LocalDateTime 직렬화 트러블슈팅

### ❓ 문제 상황
- 아래 구조의 DTO를 Redis 저장 시 에러 발생
```java
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PostDto {
    private String title;
    private LocalDateTime createdAt;
}

//////////////////////////////////////////////////

public void saveDto(String k, PostDto postDto){
    redisTemplate.opsForValue().set(k, postDto);
}
```

### 💥 발생하는 에러
```text
InvalidDefinitionException: Java 8 date/time type  `java.time.LocalDateTime` not supported by default
```

### 🔍 원인
-  Redis 문제가 아니라 **ObjectMapper의 문제**
- DTO 방식이 아닌 `localDateTime.toString()`로 저장하면 문제가 안됨
```text
Jackson 기본 설정
  → Java 8의 LocalDateTime, LocalDate 등을 **직접 직렬화 못 함**
  → 🔸 별도 모듈(JavaTimeModule) 등록이 필요함
```

### ✅ 해결 방법
- RedisConfig 내 ObjectMapper 설정 추가 후 직렬화/역직렬화 객체 주입

```java
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate( RedisConnectionFactory connectionFactory ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        
        // Code ...
        
        ObjectMapper objectMapper = new ObjectMapper();
        // ✅ JavaTimeModule 등록
        objectMapper.registerModule(new JavaTimeModule());

        // ✅ LocalDateTime을 timestamps(숫자) 대신 문자열로 직렬화
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // objectMapper 인자값 주입
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);
        
        // 주입
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        return template;
    }
}
```
#### 흐름
```text
[ PostDto (LocalDateTime 포함) ]
     ↓
[ GenericJackson2JsonRedisSerializer ]
     ↓
[ ObjectMapper ]
     ↓ JavaTimeModule 적용
     ↓ WRITE_DATES_AS_TIMESTAMPS 비활성화
[ {"title":"제목","createdAt":"2024-01-15T10:30:00"} ]
     ↓
[ Redis 저장 ]
     ↓ (역직렬화)
[ 다시 PostDto로 정확히 복원 ]
```


## Testcontainers 통합 테스트 환경 구축
> CI/CD 환경에서 Redis 없으면 테스트 불가능한 환경을 개선하기 위함
- Docker로 실제 Redis 컨테이너 띄워서 테스트 진행 가능
  - 환경 통일, CI/CD 호환

### 흐름
```text
[ 테스트 실행 ]
     ↓
[ Testcontainers가 Docker로 Redis 컨테이너 시작 ]
     ↓
[ 컨테이너의 host:port를 Spring 설정에 **동적**으로 주입 ]
     ↓
[ 테스트 코드 실행 ]
     ↓
[ 테스트 종료 후 컨테이너 자동 제거 ]
```
### 설정
#### build.gradle
```groovy
dependencies {
	// Testcontainers 
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
	testImplementation 'org.testcontainers:testcontainers'
	testImplementation 'org.testcontainers:junit-jupiter'
}
```

#### Test Code
- 추상 클래스로 분리하여 사용
- `@DynamicPropertySource`의 경우 `@ServiceConnection`로 대체가 가능함 (boot 버전 3.x 이상)
```java
@Testcontainers
public abstract class RedisContainerSupport {

    // 컨테이너 정보주입
    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7.2-alpine")
                    // 컨테이터 외부에 노출될 포트
                    .withExposedPorts(6379);

    /**
     * 컨테이너 실행 후 설정 값 동적 주입
     *
     * @param registry the spring setting
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    RedisTemplate<String, Object> redisTemplate;

    // 한건의 테스트가 끝날때다 Redis 데이터 초기화
    @AfterEach
    void tearDown() {
        redisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushAll();
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////

@SpringBootTest
@Slf4j
public class RedisTemplateUsingServiceTests extends RedisContainerSupport {

    @Autowired
    private RedisTemplateUsingService redisService;
    
    // Test Code
}    
```

## @Cacheable ?
> Spring 제공하는 캐시 표준 인터페이스
> - 구체적인 캐시 구현체(Redis, Caffeine 등)에 의존하지 않고 동일한 코드로 캐싱을 적용할 수 있는 추상화 계층
>   - Redis가 없어도 내부 메모리로 동작 
> - ✏️ [중요] AOP 기반으로 동작
>   - `@Cacheable`로 지정한 메서드를 메서드에서 호출해도 캐싱되지 않는다 --> ☠️ Self-invocation 함정 - ❌ 프록시 우회로 캐시 동작 안 함 

### 본질 짚기
> @Cacheable 은 "결과 캐싱" 도구이지, "Redis 활용 도구" 가 아님

- `@Cacheable`만으로 할 수 없는 것들이 명확함 (Redis 기준)
  - 좌석 1번을 5분 동안 점유하되, 이미 점유 중이면 실패" `(SET NX EX)`
  - "조회수를 원자적으로 1 증가" `(INCR)`
  - "대기열에 사용자 추가, 순번 반환" `(ZADD + ZRANK)`

### 선택 기준

| 상황/도구 | 주요 요구사항 | 추천 방식 | 비고 |
| :--- | :--- | :--- | :--- |
| **단순 조회 결과 캐싱** | 비싼 DB 조회 결과를 그대로 캐싱하고 싶을 때 | `@Cacheable` | 스프링 추상화 이용, 설정 간편 |
| **동적 TTL 적용** | 결과 캐싱이지만 동적 TTL(만료 시간) 설정이 필요할 때 | `RedisCacheManager` 또는 `RedisTemplate` | 커스텀 설정 필요 |
| **특수 자료구조 활용** | 카운터, 락, 대기열, 랭킹 등 Redis 명령어가 필요한 로직 | **RedisTemplate** ⭐ | Redis 전용 기능 활용 |
| **이벤트 기반 무효화** | 캐시 무효화 시점이 비즈니스 이벤트 기반일 때 (별도 시점) | `RedisTemplate.delete()` | 세밀한 제어 가능 |

### 추상화 구조
```text
[ 비즈니스 코드 (@Cacheable) ]
            ↓
[ Spring Cache 추상화 계층 ]
            ↓
[ CacheManager 인터페이스 ]
            ↓
   ┌────────┼────────┬─────────┐
   ↓        ↓        ↓         ↓
[Redis] [Caffeine] [EhCache] [...]
```

### 동작 흐름
```text
[ @Cacheable가 사용된 getPost(1) 호출 ]
        ↓
[ Redis 에서 "post:1" 조회 ]
        ↓
   ┌────┴─────────────┐
   ↓                  ↓
[Cache Hit]        [Cache Miss]
   ↓                  ↓
[즉시 리턴]        [메서드 실행 (DB 조회)]
                      ↓
                   [⭐️ 결과를 Redis 에 자동 저장]
                      ↓
                   [리턴]
```

### 설정 및 사용

#### 활성화
- `@EnableCaching`를 통해 활성화를 하지 않으면 **캐싱이 동작하지 않음** 
```java
@SpringBootApplication
@EnableCaching  // ✅ 필수
public class Application { }
```

#### 설정
- `RedisCacheManager` 설정을 Bean으로 추가해야 한다.
  - JSON 직렬화로 바꾸기 위해 RedisCacheManager를 직접 등록 (  **"기본 직렬화 방식이 부적절해서"** )
- Spring에서 생성되는 Redis key의 suffix가 `"::"`방식으로 네이밍 컨벤션에 맞게 커스텀이 필요함 

##### ✏️ 선택사항
- 1 . Object Mapper 설정을 변경하여 JSON 구조 내 `@class` 정볼를 추가하는 방법 [👎]
  - 보안상 이슈와 분산환경에서 사용하기 좋지 못함 - 내부에 class 정보가 노출됨
- 2 . `withInitialCacheConfigurations`를 설정하여 각기  Class 구조를 주입해주는 방식 [👍]
  - 대부분의 기업에서 사용하는 방식
  - 캐시별 타입을 명시하므로 `objectMapper에서 따로 activateDefaultTyping`설정 없이 역직렬화 가능 → @class 노출 없음, 보안 안전
  - 각각의 매핑 class별 TTL 설정이 가능하고 보안 안전함 
- 3 . `StringRedisTemplte` 저장 및 값을 불러오는 방식 [👍]
  - 대부분의 기업에서 사용하는 방식 - 공통 class를 생성하여 직렬화 및 역직렬화 진행
  - cache 처리를 세밀하게 처리가 가능 - 다만 코드가 길어진다는 단점은 존재함

##### Redis Config
```java
@Configuration
public class RedisConfig {
  /**
   * ObjectMapper 공통 생성
   * <br/>
   * - RedisTemplate, RedisCacheManager : 모두에 동일하게 사용
   */
  private ObjectMapper buildObjectMapper() {
    // 🔍 DTO 내 LocalTime 존재 시 "jackson.databind.exc.InvalidDefinitionException" 예외 방지
    ObjectMapper objectMapper = new ObjectMapper();
    // ✅ JavaTimeModule 등록
    objectMapper.registerModule(new JavaTimeModule());
    // ✅ LocalDateTime을 timestamps(숫자) 대신 문자열로 직렬화
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // ☠️ ObjectMapper 변경 방식 :
    // - 타입 정보를 JSON 에 포함시켜야 역직렬화 시 원래 타입으로 복원 가능
    // 보안 및 분산 환경에 옳지 못한 방향
//        objectMapper.activateDefaultTyping(
//                LaissezFaireSubTypeValidator.instance,
//                ObjectMapper.DefaultTyping.NON_FINAL,
//                JsonTypeInfo.As.PROPERTY
//        );

    return objectMapper;
  }

  /**
   * @Cacheable 이 사용하는 CacheManager
   */
  @Bean
  public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    ObjectMapper objectMapper = buildObjectMapper();

    // ✅ 기본 설정: 매칭되지 않은 캐시명에 적용 (fallback)
    RedisCacheConfiguration defaultConfig = baseConfig()
            // TTL 설정
            .entryTtl(Duration.ofMinutes(10))
            // ✅ Value 는 JSON 설정
            .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(
                            new GenericJackson2JsonRedisSerializer(objectMapper))
            );

    // ✅ 캐시별 타입 고정 설정 - 해당 keys는 cache
    // Map의 key는 cacheNames과 꼭 같아야 한다 (그렇지 않으면 cache miss로 간주)
    Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
            "post",    typedConfig(PostDto.class,    Duration.ofMinutes(30), objectMapper)
//                , "orders",   typedConfig(Order.class,   Duration.ofMinutes(5),  objectMapper)
    );

    return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .build();
  }

  /**
   * 공통 베이스: suffix, key 직렬화 등
   * <br/>
   * SpringBoot에서 자동으로 생성되는 key의 suffix "::" 형식이기에 ":"형식으로 변경함
   *
   * @return  the 공통 설정 RedisCacheConfiguration
   * */
  private RedisCacheConfiguration baseConfig() {
    return RedisCacheConfiguration.defaultCacheConfig()
            .computePrefixWith(cacheName -> cacheName + ":")
            .serializeKeysWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
            )
            .disableCachingNullValues(); // null 캐싱 방지 (선택, 권장)
  }

  /**
   * 특정 타입 전용 캐시 설정
   * @param type the Object -> class 변환할 구조
   * @param ttl   the 해당 매핑되는 key의 TTL 설정
   * @param objectMapper the 사용될 ObjectMapper
   *
   * @return  the 지정 class의 RedisCacheConfiguration
   * @param <T> the 변환할 class generic
   */
  private <T> RedisCacheConfiguration typedConfig(
          Class<T> type, Duration ttl, ObjectMapper objectMapper) {

    Jackson2JsonRedisSerializer<T> serializer =
            new Jackson2JsonRedisSerializer<>(objectMapper, type);

    return baseConfig()
            .entryTtl(ttl)
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
  }


}
```

##### `withInitialCacheConfigurations` 방식 사용 예시
- cacheNames는 무조건 config에 설정한 key와 **같아한다**
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl {
    private final PostRepository postRepository;
    private final RedisTemplate<String, Object> redisTemplate;


    // cache Miss 시 자동으로 Redis에 저장됨
    // - cacheNames는 무조건 config에 설정한 key와 같아함
    @Cacheable(cacheNames = "post", key = "#id")
    public PostDto getPost(Long id){
        log.info("is read DB");
        return postRepository.findById(id)
                .map(PostDto::from)
                .orElseThrow(() -> new RuntimeException("저장된 값을 찾을 수 없습니다."));
    }

    // key를 PathVariable 처럼 이어서 가능
    @Cacheable(cacheNames = "post.search", key = "#keyword + ':' + #page")
    public List<PostDto> search(String keyword, int page) { 
        // → Redis 키: post.search:spring:0
        return null;
    }

    // key를 객체에서 꺼내어 사용
    @Cacheable(cacheNames = "user", key = "#user.id")
    public UserDetail load(User user) { 
        return null;
    }

    // 캐싱 "전" 여부 조건 처리 (condition : boolean 방식이면 어떤식으로든 처리 가능)
    @Cacheable(cacheNames = "post", key = "#id", condition = "#id > 0")
    public PostDto getPost(Long id){
        // id가 0 이하면 캐시 자체를 안 탐 (매번 DB 조회)
        return null;
    }
  
    // 캐싱 "후" 여부 조건 처리
    @Cacheable(cacheNames = "post", key = "#id", unless = "#result == null")
    public PostDto getPost(Long id) { return null; }

    @Cacheable(cacheNames = "posts", key = "#userId", unless = "#result.isEmpty()")
    public List<PostDto> findByUser(Long userId) { return null; }
  
    @Cacheable(cacheNames = "post", key = "#id", unless = "#result?.status == 'DRAFT'")
    public PostDto getPost(Long id) { return null; }
    
}
```

## @CachePut
> 항상 실행되며 캐시를 갱신함
> - ✏️ 캐시 Hit : 메서드 실행 저장 || 캐시 Miss : 메서드 실행 후 저장
```java
@CachePut(cacheNames = "post", key = "#post.id")
public Post update(Post post) {
  return postRepository.save(post);  // 반환값이 캐시에 저장됨
}
```

## @CacheEvict
> 캐시 제거
- `allEntries` : true일 경우 캐시 영역(지정 `cacheNames`) **전체 비움**
  - 기본값 : false
- `beforeInvocation` : true일 경우 메서드 실행 전에 삭제
  - 기본값 : false

```java
@CacheEvict(
    cacheNames = "post",
    key = "#id",
    allEntries = false,        // true 면 해당 캐시 영역 전체 삭제
    beforeInvocation = false,  // true 면 메서드 실행 전에 삭제
    condition = "#id > 0"
)
public void delete(Long id) {
    postRepository.deleteById(id);
}
```

### beforeInvocation 옵션
> false 일 경우 메서드 종료 후 제가 || true 일 경우 메서드 실행 전 삭제
> - ✅ `beforeInvocation=true`는 정말 특수 케이스에서만 사용

#### 문제 사항
```text
[타임라인]
시간 0: ServerA 가 delete(1) 시작
시간 1: ServerA 가 DB 에서 post:1 삭제 (트랜잭션 안)
시간 2: ServerB 가 findById(1) 호출 → 캐시 HIT → 옛날 데이터 반환 ⚠️
시간 3: ServerA 트랜잭션 커밋
시간 4: ServerA 가 캐시 삭제 (beforeInvocation=false 인 경우)

beforeInvocation = false                      beforeInvocation = true
─────────────────────────────                 ───────────────────────────
시간 1~4 사이에 ServerB 는                       시간 0 직후 캐시 삭제됨
삭제된 데이터를 캐시에서 읽음 ⚠️                     시간 1~3 동안 ServerB 가 조회하면
                                              MISS → DB 조회 (트랜잭션 격리 수준에 따라
                                              아직 옛 데이터일 수 있지만, 어쨌든
                                              최종 일관성 회복 시점이 더 빠름)
```

#### beforeInvocation : true 의 위험 시나리오
- "데이터는 그대로인데 캐시만 비워지는" 부작용
  - update 시 비즈니스로직 내 예외처리가 발생된 경우 시나리오
  
```text
beforeInvocation = true                       beforeInvocation = false
─────────────────────────────                 ───────────────────────────
DB: 그대로 (트랜잭션 롤백) ✅                 DB: 그대로 ✅
캐시: 삭제됨 ❌                               캐시: 그대로 ✅
                                              
→ DB 와 캐시 불일치 (역방향)                  → 일관성 OK
→ 다음 조회 시 DB 재조회 (불필요)             → 캐시 HIT (효율적)
```

## @Caching
> @CachePut + @CacheEvict 사용 가능
```java
//  @CachePut + @CacheEvict 
@Caching(
    put = { @CachePut(cacheNames = "post", key = "#id") },
    evict = { @CacheEvict(cacheNames = "postList", allEntries = true) }
)
@Transactional
public Post update(Long id, String title, String content) {
    log.info("✏️ 게시글 수정: id={}", id);
    Post post = postRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Post not found: " + id));
    post.setTitle(title);
    post.setContent(content);
    post.setUpdatedAt(LocalDateTime.now());
    return postRepository.save(post);
}

// 2개의 key 제거 가능
@Caching(evict = {
    @CacheEvict(cacheNames = "post", key = "#id"),
    @CacheEvict(cacheNames = "postList", allEntries = true)
})
@Transactional
public void delete(Long id) {
    log.info("🗑️ 게시글 삭제: id={}", id);
    postRepository.deleteById(id);
}
```

## RedisCacheManager - transactionAware() 설정 
- **"트랜잭션 기준"** 캐시 삭제 여부를 설정
  - `setTransaction = true`: 캐싱 삭제는 트랜잭션 `commit` 시점까지 미뤄두고, `rollback`되면 **취소됨**
- **중요 포인트** : **트랜잭션 어노테이션이 있을 때만 의미**가 있음

### 흐름
```text
@Transactional updatePost()
├─ DB UPDATE
├─ @CachePut → Synchronization 등록 (지연)
├─ 메서드 정상 종료
├─ 트랜잭션 commit 성공
│  └─ Synchronization.afterCommit() → Redis SET 실행 ✅
└─ 결과: DB(새값) = Redis(새값) ✅
```

#### `beforeInvocation=false` 와 차이?
> 👍 transactionAware()는 `@CacheEvict` 뿐만 아니라 `@CachePut`에도 적용이 돤디. 

```text
[ beforeInvocation=false 만 사용 시 ]
   메서드 던진 예외 ──→ AOP advice 자체가 안 돔 → 캐시 안전 👍
   commit 시점 롤백 ──→ AOP advice 는 이미 실행됨 → 캐시 오염 👎

[ transactionAware() 사용 시 ]
   메서드 던진 예외 ──→ AOP advice 자체가 안 돔 → 캐시 안전 👍
   commit 시점 롤백 ──→ AOP advice 는 실행됐지만, 실제 Redis I/O 는
                      TX commit 후로 지연 등록 → 롤백 시 실행 안 함 → 캐시 안전 👍
```

#### 설정
```java
@Bean
public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    // code ..
    return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            // ✅ transaction 이 종료 후 Redis에 반영
            .transactionAware()
            .build();
}
```

## null 캐싱 방지
> 실무 권장 패턴은 아래 조건 / 설정 둘 다 사용하는 것 (Defense in Depth)
1. `unless = "#result == null"` 사용 (AOP - 메서드 단위)
2. `disableCachingNullValues()` 설정 (RedisConfig - 설정 단위)
  - 해당 설정 시 "1번"-`unless` 설정이 강제된다. (IllegalArgumentException 발생)
  - return 자체가 NULL 이어야 발생하는 문제임

### 동작 시점 비교
```text
@Cacheable(unless = "#result == null") 로 메서드 호출
       │
       ▼
┌─────────────────────────────────────┐
│  Spring Cache Abstraction (AOP)     │
│  ─ unless 여기서 평가 ◀──── ① 1차 방어선
│     null 이면 cache.put() 호출 X    │
└─────────────────────────────────────┘
       │ (unless 통과 시 cache.put 호출)
       ▼
┌─────────────────────────────────────┐
│  RedisCache 구현체                  │
│  ─ disableCachingNullValues 체크 ◀── ② 2차 방어선
│     null 이면 IllegalArgumentEx 또는 │
│     조용히 무시 (옵션 따라)          │
└─────────────────────────────────────┘
       │
       ▼
   Redis SET
```

### ⚠️ 알아둘 것
```text
`disableCachingNullValues()` 가 켜진 상태에서 unless 없이 메서드가 null 을 반환하면 IllegalArgumentException 이 발생한다.
- 실무에선 unless 를 1차로 걸고, Config 는 백업으로 설정하는 것이 실무 패턴 
```

#### IllegalArgumentException이 발생하지 않는 코드 / 발생 코드
```java
// IllegalArgumentException 미발생 -> 반환전 예외 처리
@Cacheable(cacheNames = "post", key = "#id")
public PostDto getPost(Long id){
  log.info("is read DB");
  return postRepository.findById(id)
          .map(PostDto::from)
          .orElseThrow(() -> new RuntimeException("저장된 값을 찾을 수 없습니다."));
}

// IllegalArgumentException 발생 -> null 반환
@Cacheable(cacheNames = "post", key = "#id")
public PostDto getPost(Long id){
  log.info("is read DB");
  return postRepository.findById(id)
          .map(PostDto::from)
          .orElse(null);
}
```
### null 캐싱 허용이 필요한 케이스 - (Cache Penetration)
> Cache Penetration (캐시 관통)?
> - 존재하지 않는 데이터를 반복 조회당할 때 캐시가 무용지물이 되는 현상.


#### 도입 판단 기준
> 모든 서비스에 도입할 필요는 없음
> - 도입 시 데이터 불일치를 막기 위해 CacheEvict 전략을 정교하게 짜야하는 구현 복잡도가 높아짐

| 상황 | 도입 |
|---|---|
| 외부 노출 API + 트래픽 많음 (초당 1000+) | ✅ 필요 |
| ID 가 순차적이라 추측 쉬움 (`/posts/1`, `/posts/2`...) | ✅ 필요 |
| `findById` 가 무거움 (조인 많거나 인덱스 없음) | ✅ 필요 |
| 내부 서비스, 트래픽 적음 | ❌ over-engineering |

**판단 기준**: 도입 비용(코드 복잡도) vs 방어 가치(DB 보호 효과) 비교.

#### 시나리오
```text
공격자/봇이 존재하지 않는 ID 로 100만 번 요청
   │
   ▼
GET /posts/99999999  → 캐시 MISS (없는 ID 라 당연)
   ▼
DB 조회 → null 반환
   ▼
disableCachingNullValues 라서 캐시 저장 안 함
   ▼
다음 요청 또 캐시 MISS → DB 조회 → null
   ▼
DB 가 매번 부담받음 ❌  (캐시가 무용지물)
```


#### 해결책
## 캐시 관통(Cache Penetration) 해결 전략 비교

데이터베이스에 존재하지 않는 데이터를 반복적으로 조회하여 캐시를 거치지 않고 직접 DB에 부하를 주는 현상을 방지하기 위한 주요 전략들입니다.

| 전략 | 동작 방식 | 장점 | 단점                                       |
| :--- | :--- | :--- |:-----------------------------------------|
| **null 캐싱 허용** | DB 조회 시 null인 결과를 짧은 TTL로 캐싱 | 단순하고 즉시 적용 가능 | 메모리 낭비 (존재하지 않는 ID들도 캐시 점유 - TTL을 짧게 설정) |
| **Bloom Filter** | "이 ID가 진짜 있나?"를 DB 호출 전 사전 체크 | DB 호출 0번 가능 (성능 최적화) | 별도 구조 운영 필요, 오탐(False Positive) 존재       |
| **요청 검증** | 비정상적인  패턴을 사전에 차단 | 가장 근본적인 방어 | 모든 공격 패턴 정의가 어려움                         |


## 실무 적용
- 허용 방법은 2가지가 있다.
  - 👎`RedisCacheConfiguration` 설정 시 `disableCachingNullValues()`를 제외하고 key 지정
    - ALLOW Null대상이 많아질 수록 설절 내 코드가 길어지고 유지보수가 어려워진다.
  - 👍 `boolean exists` 방식을 사용해서 DB에 직접적으로 접근을 막는 방식
```java
// 1. 검증 및 조회 전담 서비스 분리 (관심사 분리)
@Service
@RequiredArgsConstructor
public class PostQueryService {
    private final PostRepository postRepository;

    @Cacheable(cacheNames = "post-existence", key = "#id")
    public boolean exists(Long id) {
        return postRepository.existsById(id);
    }
}

/// ///////////////////////////////

// 2. 메인 비즈니스 로직
@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;
    private final PostQueryService postQueryService; // 외부 주입으로 프록시 자연스럽게 작동

    @Cacheable(cacheNames = "post", key = "#id")
    public PostDto getPost(Long id) {
        if (!postQueryService.exists(id)) {
            throw new PostNotFoundException(id);
        }
        return postRepository.findById(id)
                .map(PostDto::from)
                .orElseThrow();
    }
}
```


## 캐시 키 전략 (KeyGenerator)
> 실무에서는 KeyGenerator 는 거의 안 쓴다고 보면된다.
> - `@Cacheable(cacheNames= "foo", key = "#paramName")` <- 와 같이 SpEL 명시하는 것이 실무 방식
- 파라미터 개수가 늘거나 DTO내 필드가 늘어날 경우 정상적으로 Redis에서 **값을 찾을 수 없음**

### 문제점 요약
> SpEL 명시하지 않을 경우 Spring 내에서 자동으로 Key를 생성하기 때문이다.
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

## Key Prefix 컨벤션 — 멀티 환경
> {서비스명}:{캐시명}:{key} 구조로 진행 
> - ex) order-svc:post:1    ==> 서비스 분리가 명확하여 MSA 환경 👍
```java
@Configuration
public class RedisConfig {
    @Value("${spring.application.name}")
    private String appName;

    /**
     * 공통 베이스: prefix, key 직렬화, null 캐싱 방지 등
     * <br/>
     * SpringBoot에서 자동으로 생성되는 key의 prefix가 "::" 형식이기에 ":"형식으로 변경함
     *
     * @return  the 공통 설정 RedisCacheConfiguration
     * */
    private RedisCacheConfiguration baseConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(cacheName -> appName + ":" + cacheName + ":")
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                ;
    }

}

```