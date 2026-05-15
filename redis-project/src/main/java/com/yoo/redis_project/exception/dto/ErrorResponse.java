package com.yoo.redis_project.exception.dto;

import java.time.LocalDateTime;

/**
 * API 에러 응답 공통 포맷.
 *
 * <p>클라이언트가 에러 원인을 파악할 수 있도록
 * status / message / timestamp 를 일관되게 반환한다.
 */
public record ErrorResponse(
        int status,
        String message,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, LocalDateTime.now());
    }
}
