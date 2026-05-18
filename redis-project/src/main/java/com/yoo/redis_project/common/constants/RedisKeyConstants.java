package com.yoo.redis_project.common.constants;

/**
 * Redis에서 사용될 키 상수 모음.
 *
 * <p>모든 Redis 키는 이 클래스에서 관리한다.
 * 키 변경 시 이 파일 한 곳만 수정하면 된다.
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {}

    /** 콘서트 상세 캐시. 형식: concert:{concertId} */
    public static final String CONCERT_DETAIL = "concert:%d";

    /** 콘서트 좌석 목록 캐시. 형식: concert:{concertId}:seats */
    public static final String CONCERT_SEATS = "concert:%d:seats";

    /** 유저 프로필 캐시. 형식: user:{userId} */
    public static final String USER_PROFILE = "user:%d";

    /** 콘서트 조회수 증분 카운터. 형식: concert:{concertId}:views:delta */
    public static final String CONCERT_VIEW_DELTA = "concert:%d:views:delta";

    /** 좌석 임시 점유 락. 형식: seat:{seatId}:lock */
    public static final String SEAT_LOCK = "seat:%s:lock"; // ← 추가
}
