package com.yoo.redis_project.sapmple.dto;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@Getter
public class RequestSample {
    private Long id;
    private String title;
    private String dummy;
}
