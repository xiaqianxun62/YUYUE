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

    /** 接龙 / 列表展示的称呼：实名报名=真实姓名，匿名报名=「球友#xxxx」 */
    private String displayName;

    /** 1男 2女 */
    private Integer gender;

    /** 1匿名 0实名（Constants.ANONYMOUS_*） */
    private Integer anonymous;

    public static RegistrationEvent of(Long gameId, Long userId, String displayName,
                                       Integer gender, boolean anonymous) {
        return new RegistrationEvent(gameId, userId, displayName, gender,
                anonymous ? 1 : 0);
    }
}
