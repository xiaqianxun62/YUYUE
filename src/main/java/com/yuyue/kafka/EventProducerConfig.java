package com.yuyue.kafka;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * 事件生产者的装配开关。
 *
 * 本地没起 Kafka 时设 KAFKA_ENABLED=false，只装配空实现：
 * 不建立任何连接、不注册监听容器，事件降级为日志，业务接口照常可用。
 */
@Configuration
public class EventProducerConfig {

    @Bean
    @ConditionalOnProperty(name = "yuyue.kafka.enabled", havingValue = "true", matchIfMissing = true)
    public EventProducer kafkaEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        return new EventProducer(kafkaTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "yuyue.kafka.enabled", havingValue = "false")
    public EventProducer noopEventProducer() {
        return new EventProducer();
    }
}
