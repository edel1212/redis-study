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
    public static final String SEAT_LOCK = "seat:%d:lock";

    /** 콘서트 랭킹. 고정키 형식: concert:ranking */
    public static final String CONCERT_RANKING = "concert:ranking";

    /** 지정 날짜 콘서트 조회한 사람. 고정키 형식: concert:{concertId}:viewers:{yyyyMMdd}  */
    public static final String CONCERT_DAILY_VIEWERS = "concert:%d:viewers:%s";

    // virtual room

    /** 가상 Room 대기열 등록 "시간 기준 sorted set" 방식. 형식: waiting:concert:{concertId} {timestamp} "{userId}"  */
    public static final String WAITING_QUEUE   = "waiting:concert:%d";
    /** 지정 콘서트 내 입장 가능한 사람 목록 "set" 방식. 형식: waiting:concert:{concertId}:entered  */
    public static final String WAITING_ENTERED = "waiting:concert:%d:entered";
    /** 좌석 점유 가능 인증 토큰 "set" 방식. 형식: waiting:concert:{id}:token:{userId} "{uuid}" EX 300  */
    public static final String WAITING_TOKEN   = "waiting:concert:%d:token:%d";
}
