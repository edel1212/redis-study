package com.yoo.redis_project.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Comment("사용자 정보")
@Table(name = "users")   // "user" 는 PostgreSQL 예약어
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("사용자 식별ID")
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    @Comment("사용자 email")
    private String email;

    @Column(nullable = false, length = 50)
    @Comment("사용자명")
    private String name;

    @Builder
    private UserEntity(String email, String name) {
        this.email = email;
        this.name = name;
    }
}
