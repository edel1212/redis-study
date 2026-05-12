package com.yoo.redis_project.sapmple.controller;

import com.yoo.redis_project.sapmple.dto.RequestSample;
import com.yoo.redis_project.sapmple.dto.PostDto;
import com.yoo.redis_project.sapmple.service.KeyGeneratorServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/key-gen")
@RequiredArgsConstructor
public class KeyGeneratorController {
    private final KeyGeneratorServiceImpl keyGenerateService;

    // OK
    @GetMapping("/{id}/type-one")
    public ResponseEntity<PostDto> getPost(@PathVariable Long id){
        return ResponseEntity.ok(keyGenerateService.getPostType1(
                RequestSample.builder().id(id).title("이건 가능할것")
                .dummy("dum").build())
        );
    }

    // NO
    @GetMapping("/{id}/type-two")
    public ResponseEntity<PostDto> getPost2(@PathVariable Long id){
        return ResponseEntity.ok(keyGenerateService.getPostType2(id, 34L));
    }

    // NO
    @GetMapping("/{id}/type-three")
    public ResponseEntity<PostDto> getPost3(@PathVariable Long id){
        return ResponseEntity.ok(keyGenerateService.getPostType3(
                RequestSample.builder().id(id).title("이건 불가능")
                        .dummy("dum").build())
        );
    }
}
