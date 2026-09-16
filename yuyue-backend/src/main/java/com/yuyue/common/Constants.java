package com.yuyue.common;

/**
 * 全局常量：Redis Key、Kafka Topic、业务枚举值
 */
public final class Constants {

    private Constants() {
    }

    /* ---------- Redis Keys ---------- */

    /** 积分榜 ZSet：member=userId, score=rating */
    public static final String RANKING_ZSET = "yuyue:ranking:elo";

    /** 球局报名人数 Hash：field=gameId, value=count */
    public static final String GAME_REG_COUNT = "yuyue:game:reg:count";

    /** JWT 黑名单（登出后 token 失效）：member=token */
    public static final String JWT_BLACKLIST = "yuyue:jwt:blacklist";

    /* ---------- Kafka Topics ---------- */

    /** 报名事件：机器人消费后同步微信群接龙 */
    public static final String TOPIC_REGISTRATION = "yuyue-registration";

    /** 对局结算事件：消费后落库积分变更 */
    public static final String TOPIC_MATCH_SETTLE = "yuyue-match-settle";

    /* ---------- 枚举值 ---------- */

    /** 性别：男 */
    public static final int GENDER_MALE = 1;
    /** 性别：女 */
    public static final int GENDER_FEMALE = 2;

    /** 球局状态：报名中 */
    public static final int GAME_STATUS_OPEN = 0;
    /** 球局状态：已编排 */
    public static final int GAME_STATUS_ARRANGED = 1;

    /** 对局形式：单打/男双/女双/混双 */
    public static final int FORMAT_SINGLES = 1;
    public static final int FORMAT_MEN_DOUBLES = 2;
    public static final int FORMAT_WOMEN_DOUBLES = 3;
    public static final int FORMAT_MIXED_DOUBLES = 4;
    /** 随机双打：不分性别随机组队，仅用于 ELO 试算，不落库 */
    public static final int FORMAT_RANDOM_DOUBLES = 5;

    /** 对局胜方：A队 / B队 */
    public static final int WINNER_A = 1;
    public static final int WINNER_B = 2;

    /** 对局结算状态 */
    public static final int SETTLE_PENDING = 0;
    public static final int SETTLE_DONE = 1;
}
