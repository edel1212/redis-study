package com.yoo.redis_project.exception.custom;

/**
 * 요청한 리소스가 존재하지 않을 때 발생하는 예외.
 *
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
