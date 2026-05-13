package com.yoo.redis_project.domain.enums;

import lombok.Getter;

@Getter
public enum SeatStatus {
    AVAILABLE("예매 가능"),
    HELD("임시 점유 중"),
    SOLD("예매 완료")
    ;

    private final String description;

    SeatStatus(String description){
        this.description = description;
    }

}

