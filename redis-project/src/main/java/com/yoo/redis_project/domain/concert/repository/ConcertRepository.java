package com.yoo.redis_project.domain.concert.repository;

import com.yoo.redis_project.domain.concert.entity.ConcertEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConcertRepository extends JpaRepository<ConcertEntity, Long>  {
}
