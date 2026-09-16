package com.yuyue.kafka;

import com.yuyue.common.Constants;
import com.yuyue.event.RegistrationEvent;
import lombok.extern.slf4j.Slf4j;
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
public class RegistrationConsumer {

    @KafkaListener(topics = Constants.TOPIC_REGISTRATION, groupId = "yuyue-group")
    public void onRegistration(RegistrationEvent event) {
        log.info("[clawbot] 收到报名事件: game={}, user={}, name={}, gender={}",
                event.getGameId(), event.getUserId(), event.getAnonymousName(),
                event.getGender() == 1 ? "男" : "女");
        pushToWechatGroup(event);
    }

    /**
     * TODO: 接入 clawbot 真实接口，把报名信息追加到微信群接龙
     */
    private void pushToWechatGroup(RegistrationEvent event) {
        // 示例: clawbotClient.appendChain(event.getGameId(),
        //         event.getAnonymousName() + (event.getGender() == 1 ? "(男)" : "(女)"));
        log.info("[clawbot] 已同步到微信群接龙: {} 加入球局 {} 接龙", event.getAnonymousName(), event.getGameId());
    }
}
