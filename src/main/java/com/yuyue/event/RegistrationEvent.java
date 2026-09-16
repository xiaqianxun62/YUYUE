package com.yuyue.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 报名事件：发布到 Kafka，由 clawbot 消费后同步到微信群接龙
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationEvent {

    private Long gameId;

    private Long userId;

    /** 匿名昵称 */
    private String anonymousName;

    /** 1男 2女 */
    private Integer gender;

    public static RegistrationEvent of(Long gameId, Long userId, String anonymousName, Integer gender) {
        return new RegistrationEvent(gameId, userId, anonymousName, gender);
    }
}
