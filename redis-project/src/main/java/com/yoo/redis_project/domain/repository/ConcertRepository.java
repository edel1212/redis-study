package com.yoo.redis_project.domain.repository;

import com.yoo.redis_project.domain.entity.ConcertEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConcertRepository extends JpaRepository<ConcertEntity, Long>  {
}
