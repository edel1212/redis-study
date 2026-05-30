package com.yoo.redis_project.domain.user.repository;

import com.yoo.redis_project.domain.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /**
     * 이메일로 사용자를 조회한다.
     * <p>email 컬럼에는 unique 제약이 걸려 있어 결과는 0건 또는 1건.
     *
     * @param email 사용자 이메일
     * @return 사용자 (없으면 Optional.empty())
     */
    Optional<UserEntity> findByEmail(String email);
}
