package com.yoo.redis_project.sapmple.dto;

import com.yoo.redis_project.sapmple.entity.PostEntity;
import lombok.*;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
public class RequestPost {
    private String title;

    public PostEntity toEntity() {
        return PostEntity.builder()
                .title(this.title)
                .build();
    }
}
