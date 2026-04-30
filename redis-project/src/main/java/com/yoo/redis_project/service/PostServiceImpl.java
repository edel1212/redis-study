package com.yoo.redis_project.service;

import com.yoo.redis_project.dto.PostDto;
import com.yoo.redis_project.dto.RequestPost;
import com.yoo.redis_project.entity.PostEntity;
import com.yoo.redis_project.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl {
    private final PostRepository postRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public void writePost(RequestPost requestPost){
        PostEntity entity = requestPost.toEntity();
        // DB 저장
        postRepository.save(entity);
        //  💀 해당 로직에 Redis 내 Key를 저장하는 행위를 하지 않음
        // - 관심사 분리 / 트랜잭션 오류 시 Redis에는 저장되는 문제가 발생
    }

    public List<PostDto> getList(){
        return postRepository.findAll().stream().map(PostDto::from).toList();
    }

    // @Cacheable 사용 - cache Miss 시 자동으로 Redis에 저장됨
    @Cacheable(cacheNames = "post", key = "#id")
    public PostDto getPost(Long id){
        log.info("is read DB");
        return postRepository.findById(id)
                .map(PostDto::from)
                .orElseThrow(() -> new RuntimeException("저장된 값을 찾을 수 없습니다."));
    }

    public PostDto getPostWriteLogic(Long id){
        String key = "post:" + id;
        // [cache hit] 캐시 조회
        PostDto cached = (PostDto) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            log.info("cache hit : {}", cached);
            return cached;
        } // if

        log.info("cache miss read DB");
        // [cache miss] DB 조회
        return postRepository.findById(id)
                .map(PostDto::from)
                .orElseThrow(() -> new RuntimeException("저장된 값을 찾을 수 없습니다."));
    }


}
