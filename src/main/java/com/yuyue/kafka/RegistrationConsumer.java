package com.yuyue.kafka;

import com.yuyue.common.Constants;
import com.yuyue.event.RegistrationCancelEvent;
import com.yuyue.event.RegistrationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Objects;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 报名事件消费者 —— clawbot 机器人同步微信群接龙的接入点。
 *
 * 当前实现为日志桩：接入真实微信群机器人时，把 pushToWechatGroup
 * 替换为 clawbot 的 HTTP/Webhook 调用即可，事件顺序即接龙顺序。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "yuyue.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class RegistrationConsumer {

    @KafkaListener(topics = Constants.TOPIC_REGISTRATION, groupId = "yuyue-group")
    public void onRegistration(RegistrationEvent event) {
        log.info("[clawbot] 收到报名事件: game={}, user={}, name={}, gender={}, {}",
                event.getGameId(), event.getUserId(), event.getDisplayName(),
                event.getGender() == 1 ? "男" : "女",
                Objects.equals(event.getAnonymous(), Constants.ANONYMOUS_NO) ? "实名" : "匿名");
        pushToWechatGroup(event);
    }

    @KafkaListener(topics = Constants.TOPIC_REGISTRATION_CANCEL, groupId = "yuyue-group")
    public void onRegistrationCancel(RegistrationCancelEvent event) {
        log.info("[clawbot] 收到取消报名事件: game={}, user={}, name={}",
                event.getGameId(), event.getUserId(), event.getDisplayName());
        removeFromWechatGroup(event);
    }

    /**
     * TODO: 接入 clawbot 真实接口，把报名信息追加到微信群接龙
     */
    private void pushToWechatGroup(RegistrationEvent event) {
        // 示例: clawbotClient.appendChain(event.getGameId(),
        //         event.getDisplayName() + (event.getGender() == 1 ? "(男)" : "(女)"));
        log.info("[clawbot] 已同步到微信群接龙: {} 加入球局 {} 接龙", event.getDisplayName(), event.getGameId());
    }

    /**
     * TODO: 接入 clawbot 真实接口，把该人从群接龙里删除
     */
    private void removeFromWechatGroup(RegistrationCancelEvent event) {
        // 示例: clawbotClient.removeFromChain(event.getGameId(), event.getDisplayName());
        log.info("[clawbot] 已从微信群接龙移除: {} 退出球局 {}", event.getDisplayName(), event.getGameId());
    }
}
