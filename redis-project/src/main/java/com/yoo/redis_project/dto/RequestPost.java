package com.yoo.redis_project.dto;

import com.yoo.redis_project.entity.PostEntity;
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
