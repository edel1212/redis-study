package com.yoo.redis_project.sapmple.repository;

import com.yoo.redis_project.sapmple.entity.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<PostEntity, Long> {
}
