package com.yuyue.kafka;

import com.yuyue.common.Constants;
import com.yuyue.event.MatchSettleEvent;
import com.yuyue.event.RegistrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka 事件生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** 报名事件 → clawbot 消费，同步微信群接龙 */
    public void sendRegistration(RegistrationEvent event) {
        kafkaTemplate.send(Constants.TOPIC_REGISTRATION, String.valueOf(event.getGameId()), event);
        log.info("Kafka 报名事件已发送: topic={}, game={}, user={}",
                Constants.TOPIC_REGISTRATION, event.getGameId(), event.getUserId());
    }

    /** 对局结算事件 → 积分消费者，异步完成 ELO 落库 */
    public void sendMatchSettle(MatchSettleEvent event) {
        kafkaTemplate.send(Constants.TOPIC_MATCH_SETTLE, String.valueOf(event.getMatchId()), event);
        log.info("Kafka 结算事件已发送: topic={}, match={}",
                Constants.TOPIC_MATCH_SETTLE, event.getMatchId());
    }
}
