package com.yoo.redis_project.domain.enums;

public enum WaitingStatus {
    WAITING,        // 대기 중
    ENTERED,        // 입장 완료
    EXPIRED,        // 토큰 시간 만료
    NOT_IN_QUEUE    // 대기열에 없음
}