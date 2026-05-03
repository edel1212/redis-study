package com.yoo.redis_project.controller;

import com.yoo.redis_project.dto.PostDto;
import com.yoo.redis_project.dto.RequestPost;
import com.yoo.redis_project.service.PostServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {
    private final PostServiceImpl postService;

    @PostMapping
    public ResponseEntity<Void> writePost(@RequestBody RequestPost requestPost){
        postService.writePost(requestPost);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<PostDto>> getAllPost(){
        return ResponseEntity.ok(postService.getList());
    }

    @GetMapping("/{id}/by-redis-tempalte")
    public ResponseEntity<PostDto> getPostWriteLogic(@PathVariable Long id){
        return ResponseEntity.ok(postService.getPostByRedisTemplate(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostDto> getPost(@PathVariable Long id){
        return ResponseEntity.ok(postService.getPost(id));
    }


    @PatchMapping("/{id}")
    public ResponseEntity<PostDto> updatePost(@PathVariable Long id, @RequestBody RequestPost requestPost){
        return ResponseEntity.ok(postService.update(id, requestPost));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Long> deletePost(@PathVariable Long id){
        return ResponseEntity.ok(postService.deletePost(id));
    }
}
