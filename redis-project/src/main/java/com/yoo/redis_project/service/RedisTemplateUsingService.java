package com.yoo.redis_project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Set;

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

    // 저장 - with TTL
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
