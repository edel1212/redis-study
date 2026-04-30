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
> - AOP 기반으로 동작

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
    
