package com.yuyue.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 取消报名事件：发布到 Kafka，由 clawbot 消费后把该人从微信群接龙移除
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationCancelEvent {

    private Long gameId;

    private Long userId;

    /** 与报名时一致的称呼，便于机器人定位接龙里的那一条 */
    private String displayName;

    public static RegistrationCancelEvent of(Long gameId, Long userId, String displayName) {
        return new RegistrationCancelEvent(gameId, userId, displayName);
    }
}
