package com.yoo.redis_project.sapmple.entity;

import com.yoo.redis_project.sapmple.dto.RequestPost;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity(name = "post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@Builder
public class PostEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    /**
     * 파라미터로 전달 받은 DTO
     *
     * @param requestPost the request Post
     */
    public void updateTitle(RequestPost requestPost){
        this.title = requestPost.getTitle();
    }
}
