package com.yoo.redis_project.sapmple.dto;

import com.yoo.redis_project.sapmple.entity.PostEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class PostDto  implements Serializable {
private Long id;
    private String title;
    private LocalDateTime createdAt;

    public static PostDto from(PostEntity entity) {
        return PostDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .createdAt(entity.getCreatedAt())
                .build();
    }

}
