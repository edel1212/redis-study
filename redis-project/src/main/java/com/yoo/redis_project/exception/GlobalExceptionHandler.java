package com.yoo.redis_project.exception;

import com.yoo.redis_project.exception.custom.ResourceNotFoundException;
import com.yoo.redis_project.exception.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리기.
 *
 * <p>Controller 계층에서 발생한 예외를 일관된 HTTP 응답으로 변환한다.
 * 각 ExceptionHandler는 단일 예외 타입만 담당한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 리소스 미존재 → 404
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
        log.warn("[GlobalExceptionHandler] 리소스 없음: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, e.getMessage()));
    }

    /**
     * 잡히지 않은 예외 → 500
     * 운영에서 예상치 못한 예외를 놓치지 않기 위한 안전망
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("[GlobalExceptionHandler] 예상치 못한 예외 발생", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(500, "서버 오류가 발생했습니다."));
    }
}
