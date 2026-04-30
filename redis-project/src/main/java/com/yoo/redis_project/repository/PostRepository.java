package com.yoo.redis_project.repository;

import com.yoo.redis_project.entity.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<PostEntity, Long> {
}
