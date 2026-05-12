package com.yoo.redis_project.sapmple.service;

import com.yoo.redis_project.sapmple.dto.PostDto;
import com.yoo.redis_project.sapmple.dto.RequestPost;
import com.yoo.redis_project.sapmple.entity.PostEntity;
import com.yoo.redis_project.sapmple.repository.PostRepository;
import com.yoo.redis_project.utils.RedisCacheHelper;
import com.yoo.redis_project.utils.TtlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl {
    private final PostRepository postRepository;
    // redis String을 Helper class로 사용
    private final RedisCacheHelper redisCacheHelper;

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

    public PostDto getPostByRedisTemplate(Long id){
        String key = "post:" + id;
        // [cache hit] 캐시 조회
        Optional<PostDto> optionalCached =  redisCacheHelper.get(key, PostDto.class);
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
        redisCacheHelper.set(key, postDto);

        return postDto;
    }


    ////////////////////////////////////////////////////////////////////////////////////////

    // [ 항상 실행 + 캐시 갱신 ] 캐싱 update
    // - 업데이트 (write-through 효과)
    @Transactional
    @CachePut(cacheNames = "post", key = "#id")
    public PostDto update(Long id, RequestPost requestPost){
        PostEntity post = postRepository.findById(id)
                .orElseThrow(()->new RuntimeException("Not Found Post"));
        post.updateTitle(requestPost);
        return PostDto.from(post);
    }


    // 캐싱 delete
    @CacheEvict(
            cacheNames = "post"
            , key = "#id"
            // 캐시 영역 전체 삭제 여부 (default - false)
            // ✏️ true일 경우 같은 "cacheNames" 갖는 대상을 전부 삭제함
            , allEntries = true
            // 메서드 실형 전 삭제 여부 (default : false)
            // - 예외 발생 시 캐시 삭제가 안됨 (DB만 데이터가 삭제되고 매시가 남은 케이스)
            // false 가 기본값인 진짜 이유 -> 캐시는 "DB 보호막" 역할도 하기 때문
            , beforeInvocation = false
            , condition = "#id > 0"
    )
    public Long deletePost(Long id){
        PostEntity post = postRepository.findById(id)
                .orElseThrow(()->new RuntimeException("Not Found Post"));
        postRepository.delete(post);
        return id;
    }

    /**
     * 수정 - @CachePut + @CacheEvict (목록 캐시 무효화)
     */
    @Caching(
            put = { @CachePut(cacheNames = "post", key = "#id") },
            evict = { @CacheEvict(cacheNames = "postList", allEntries = true) }
    )
    @Transactional
    public PostDto updateAndEvict(Long id, RequestPost requestPost) {
        log.info("✏️ 게시글 수정: id={}", id);
        PostEntity post = postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + id));
        post.updateTitle(requestPost);
        return PostDto.from(post);
    }

    /**
     * 삭제 - @CacheEvict 다수
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = "post", key = "#id"),
            @CacheEvict(cacheNames = "postList", allEntries = true)
    })
    @Transactional
    public void deleteByMulti(Long id) {
        log.info("🗑️ 게시글 삭제: id={}", id);
        postRepository.deleteById(id);
    }

    ////////////////////////////////////////////////////////////////////////////////////////

    // Jitter를 사용해서 TTL 설정
    public PostDto updatePostByJitter(Long id, RequestPost requestPost){
        String key = "post:" + id;
        // [cache hit] 캐시 조회
        Optional<PostDto> optionalCached =  redisCacheHelper.get(key, PostDto.class);
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
        Duration ttlByJitter =  TtlUtils.jitter(Duration.ofMinutes(10), .2);
        redisCacheHelper.set(key, postDto, ttlByJitter);

        return postDto;
    }

}
