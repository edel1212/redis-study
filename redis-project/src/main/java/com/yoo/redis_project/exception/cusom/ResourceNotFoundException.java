package com.yoo.redis_project.exception.cusom;

/**
 * 요청한 리소스가 존재하지 않을 때 발생하는 예외.
 *
 * @param message 어떤 리소스가 없는지 명시 (예: "콘서트 없음. id=1")
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
