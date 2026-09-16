package com.yuyue.kafka;

import com.yuyue.common.Constants;
import com.yuyue.event.MatchSettleEvent;
import com.yuyue.event.RegistrationCancelEvent;
import com.yuyue.event.RegistrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Kafka 事件生产者。
 *
 * Broker 不可用时不抛异常打断业务主流程（报名、上报对局照常成功），只记录告警；
 * 未启用 Kafka（yuyue.kafka.enabled=false）时为空实现，事件直接跳过。
 */
@Slf4j
@RequiredArgsConstructor
public class EventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** 空实现：无 KafkaTemplate，所有事件只落日志 */
    public EventProducer() {
        this.kafkaTemplate = null;
    }

    /** 报名事件 → clawbot 消费，同步微信群接龙 */
    public void sendRegistration(RegistrationEvent event) {
        if (send(Constants.TOPIC_REGISTRATION, String.valueOf(event.getGameId()), event)) {
            log.info("Kafka 报名事件已发送: topic={}, game={}, user={}",
                    Constants.TOPIC_REGISTRATION, event.getGameId(), event.getUserId());
        }
    }

    /** 取消报名事件 → clawbot 消费，把该人从群接龙移除 */
    public void sendRegistrationCancel(RegistrationCancelEvent event) {
        if (send(Constants.TOPIC_REGISTRATION_CANCEL, String.valueOf(event.getGameId()), event)) {
            log.info("Kafka 取消报名事件已发送: topic={}, game={}, user={}",
                    Constants.TOPIC_REGISTRATION_CANCEL, event.getGameId(), event.getUserId());
        }
    }

    /** 对局结算事件 → 积分消费者，异步完成 ELO 落库 */
    public void sendMatchSettle(MatchSettleEvent event) {
        if (send(Constants.TOPIC_MATCH_SETTLE, String.valueOf(event.getMatchId()), event)) {
            log.info("Kafka 结算事件已发送: topic={}, match={}",
                    Constants.TOPIC_MATCH_SETTLE, event.getMatchId());
        }
    }

    /** @return true=已投递到 Kafka；false=跳过/失败（只告警，不阻断业务） */
    private boolean send(String topic, String key, Object payload) {
        if (kafkaTemplate == null) {
            log.warn("Kafka 未启用（KAFKA_ENABLED=false），跳过事件: topic={}, key={}", topic, key);
            return false;
        }
        try {
            kafkaTemplate.send(topic, key, payload).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("Kafka 事件异步发送失败: topic={}, key={}, err={}", topic, key, String.valueOf(ex.getMessage()));
                }
            });
            return true;
        } catch (Exception e) {
            log.warn("Kafka 不可用，事件已降级跳过: topic={}, key={}, err={}", topic, key, String.valueOf(e.getMessage()));
            return false;
        }
    }
}
