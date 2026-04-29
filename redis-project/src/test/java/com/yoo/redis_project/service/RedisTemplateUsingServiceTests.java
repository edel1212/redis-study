package com.yoo.redis_project.service;

import com.yoo.redis_project.dto.PostDto;
import com.yoo.redis_project.support.RedisContainerSupport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Slf4j
public class RedisTemplateUsingServiceTests extends RedisContainerSupport {

    @Autowired
    private RedisTemplateUsingService redisService;

    // 공통 키 생성 유틸
    private String createKey(String prefix) {
        return prefix + ":" + UUID.randomUUID();
    }

    // --- 1. Key & String 테스트 ---

    @Test
    @DisplayName("String 저장, 조회 및 삭제 테스트")
    void stringAndKeyTest() {
        String key = createKey("string");
        String val = "hello-redis";

        // 저장 및 단건 조회
        redisService.saveString(key, val);
        assertThat(redisService.getString(key)).isEqualTo(val);

        // 삭제
        redisService.deleteKey(key);
        assertThat(redisService.getString(key)).isEqualTo("null");
    }

    @Test
    @DisplayName("DTO 저장")
    void stringDto() {
        String key = createKey("dto");
        PostDto postDto = PostDto.builder()
                .title("DTO 저장 테스트")
                .createdAt(LocalDateTime.now())
                .build();
        redisService.saveDto(key, postDto);
    }

    @Test
    @DisplayName("다건 삭제 테스트")
    void deleteKeysTest() {
        String k1 = createKey("del1");
        String k2 = createKey("del2");
        redisService.saveString(k1, "v1");
        redisService.saveString(k2, "v2");

        redisService.deleteKeys(Arrays.asList(k1, k2));

        assertThat(redisService.getString(k1)).isEqualTo("null");
        assertThat(redisService.getString(k2)).isEqualTo("null");
    }

    // --- 2. List 테스트 ---

    @Test
    @DisplayName("List LPush/RPush 및 Pop 테스트")
    void listTest() {
        String key = createKey("list");

        // saveList 내부에서 L, R 하나씩 추가함
        redisService.saveList(key, "data");

        List<Object> list = redisService.getList(key);
        log.info("List: {}", list);

        assertThat(list).hasSize(2);

        // Pop (현재 코드상 LeftPop 실행됨)
        Object popped = redisService.getPop(key);
        log.info("Popped: {}", popped);

        assertThat(redisService.getList(key)).hasSize(1);
    }

    // --- 3. Set 테스트 ---

    @Test
    @DisplayName("Set 멤버 추가 및 사이즈 조회")
    void setTest() {
        String key = createKey("set");

        redisService.saveSet(key, "m1");
        redisService.saveSet(key, "m1"); // 중복
        redisService.saveSet(key, "m2");

        Set<Object> members = redisService.getSet(key);
        Long size = redisService.getSetSize(key);

        assertThat(size).isEqualTo(2);
        assertThat(members).contains("m1", "m2");
    }

    // --- 4. Sorted Set 테스트 ---

    @Test
    @DisplayName("Sorted Set 스코어 정렬 및 Pop 테스트")
    void sortedSetTest() {
        String key = createKey("zset");

        redisService.saveSortedSet(key, "user1", 10);
        redisService.saveSortedSet(key, "user2", 30);
        redisService.saveSortedSet(key, "user3", 20);

        // 오름차순 (10, 20, 30)
        Set<Object> asc = redisService.getSortedSetByAsc(key);
        assertThat(asc).containsExactly("user1", "user3", "user2");

        // 내림차순 (30, 20, 10)
        Set<Object> desc = redisService.getSortedSetByDesc(key);
        assertThat(desc).containsExactly("user2", "user3", "user1");

        // Max Pop (30점인 user2)
        Object max = redisService.getSortedSetMaxPop(key);
        assertThat(max).isEqualTo("user2");
    }

    // --- 5. Hash 테스트 ---

    @Test
    @DisplayName("Hash 필드 저장 및 전체 Value 조회")
    void hashTest() {
        String key = createKey("hash");

        redisService.saveHash(key, "field1", "val1");
        redisService.saveHash(key, "field2", "val2");

        // 단건 필드 조회
        assertThat(redisService.getHashValue(key, "field1")).isEqualTo("val1");

        // 모든 키(필드명) 조회
        Set<Object> hashKeys = redisService.getHashKeys(key);
        assertThat(hashKeys).contains("field1", "field2");

        // 모든 값 조회
        List<Object> hashValues = redisService.getHashValues(key);
        assertThat(hashValues).contains("val1", "val2");
    }

    @Test
    @DisplayName("만료 시간 설정 테스트 (초 단위 & 지정 날짜)")
    void expireTest() throws InterruptedException {
        String key1 = "test:expire:sec:" + UUID.randomUUID();
        String key2 = "test:expire:date:" + UUID.randomUUID();

        redisService.saveString(key1, "value1");
        redisService.saveString(key2, "value2");

        // 1. 초 단위 만료 설정 (60초)
        redisService.setExpire(key1, 60L);

        // 2. 지정 날짜 기준 만료 설정 (현재 시간 + 1시간)
        Date targetDate = new Date(System.currentTimeMillis() + 3600000);
        redisService.setExpireAt(key2, targetDate);

        // 검증: 남은 시간(TTL) 확인
        Long ttl1 = redisService.getExpire(key1);
        Long ttl2 = redisService.getExpire(key2);

        log.info("Key1 TTL (seconds): {}", ttl1);
        log.info("Key2 TTL (seconds): {}", ttl2);

        // 60초 설정했으므로 0보다 크고 60보다 작거나 같아야 함
        assertThat(ttl1).isPositive().isLessThanOrEqualTo(60L);

        // 1시간(3600초) 설정했으므로 0보다 크고 3600보다 작거나 같아야 함
        assertThat(ttl2).isPositive().isLessThanOrEqualTo(3600L);
    }

    @Test
    @DisplayName("만료 후 키 삭제 확인 테스트")
    void expireRemovalTest() throws InterruptedException {
        String key = "test:expire:fast:" + UUID.randomUUID();

        // 2초 뒤 만료 설정
        redisService.saveString(key, "temp");
        redisService.setExpire(key, 2L);

        // 3초 대기 (Thread.sleep은 예외 처리가 필요합니다)
        Thread.sleep(3000);

        // 키가 존재하지 않아야 함
        String result = redisService.getString(key);
        assertThat(result).isEqualTo("null");
    }
}