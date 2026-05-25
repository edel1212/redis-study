package com.yoo.redis_project.exception;

import com.yoo.redis_project.exception.custom.BadRequestException;
import com.yoo.redis_project.exception.custom.ResourceNotFoundException;
import com.yoo.redis_project.exception.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.stream.Collectors;

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

    /**
     * DTO (@Valid) 검증 실패 시 잡히는 예외 → 400
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        log.warn("[GlobalExceptionHandler] DTO 검증 실패");

        // 여러 필드에서 에러가 날 수 있으므로 문장으로 합치거나 첫 번째 에러를 가져옵니다.
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> String.format("[%s]: %s", error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                // 💡 반환 타입을 ErrorResponse로 일치시킵니다.
                .body(ErrorResponse.of(400, errorMessage));
    }

    /**
     * PathVariable이나 Header 검증 실패 시 잡히는 예외 → 400
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        log.warn("[GlobalExceptionHandler] 파라미터/헤더 검증 실패: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST) // 💡 검증 실패이므로 500 대신 400(Bad Request)이 적절합니다.
                .body(ErrorResponse.of(400, ex.getMessage()));
    }

    /**
     * 필수 HTTP 헤더가 누락되었을 때 발생하는 예외 처리 → 400
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeaderException(MissingRequestHeaderException ex) {
        log.warn("[GlobalExceptionHandler] 필수 헤더 누락: {}", ex.getHeaderName());

        String errorMessage = String.format("필수 헤더가 누락되었습니다: %s", ex.getHeaderName());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, errorMessage));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidationException(HandlerMethodValidationException ex) {
        log.warn("[GlobalExceptionHandler] 메서드 파라미터 검증 실패");

        // 발생한 모든 검증 에러 메시지를 하나로 합칩니다.
        String errorMessage = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, errorMessage));
    }

    /**
     * 비즈니스 로직 상 잘못된 요청 처리 → 400 Bad Request
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException e) {
        log.warn("[GlobalExceptionHandler] 잘못된 요청 발생: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(400, e.getMessage()));
    }
}
