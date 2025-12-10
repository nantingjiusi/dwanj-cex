package com.remus.dwanjcex.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remus.dwanjcex.disruptor.event.OrderCreatedEvent;
import com.remus.dwanjcex.kafka.dto.KafkaOrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Kafka生产者服务。
 * 负责将系统中的关键业务事件发送到Kafka消息队列。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/18 10:00
 */
@Slf4j
@Service
public class KafkaProducerService {

    private static final String TOPIC_ORDERS = "orders-topic";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 监听订单创建事件（在事务成功提交后），并将包含orderId的订单信息发送到Kafka。
     *
     * @param event 订单创建事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreation(OrderCreatedEvent event) {
        log.info("接收到订单创建的事务后事件，准备发送到Kafka: orderId={}", event.getOrderId());
        try {
            // 创建包含orderId和DTO的消息体
            KafkaOrderMessage message = new KafkaOrderMessage(event.getOrderId(), event.getOrderDto());
            // 将消息体序列化为JSON字符串
            String messagePayload = objectMapper.writeValueAsString(message);
            // 发送消息到Kafka
            kafkaTemplate.send(TOPIC_ORDERS, messagePayload).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("发送订单消息到Kafka失败: orderId={}, error={}", event.getOrderId(), ex.getMessage(), ex);
                } else {
                    log.info("成功发送订单消息到Kafka: orderId={}, topic={}, offset={}",
                            event.getOrderId(),
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("序列化KafkaOrderMessage为JSON时失败: orderId={}", event.getOrderId(), e);
        }
    }
}
