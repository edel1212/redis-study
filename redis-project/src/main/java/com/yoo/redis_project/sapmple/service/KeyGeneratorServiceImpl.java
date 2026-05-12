package com.yoo.redis_project.sapmple.service;

import com.yoo.redis_project.sapmple.dto.RequestSample;
import com.yoo.redis_project.sapmple.dto.PostDto;
import com.yoo.redis_project.sapmple.entity.PostEntity;
import com.yoo.redis_project.sapmple.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

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
