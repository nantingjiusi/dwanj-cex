package com.remus.dwanjcex.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remus.dwanjcex.disruptor.event.OrderCancelEvent;
import com.remus.dwanjcex.disruptor.event.OrderCreatedEvent;
import com.remus.dwanjcex.kafka.dto.KafkaOrderMessage;
import com.remus.dwanjcex.wallet.entity.dto.CancelOrderDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Kafka生产者服务。
 * 负责将系统中的关键业务事件发送到Kafka消息队列。
 */
@Slf4j
@Service
public class KafkaProducerService {

    private static final String TOPIC_ORDERS = "orders-topic";
    private static final String TOPIC_CANCEL_ORDERS = "cancel-orders-topic";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 监听订单创建事件（在事务成功提交后），并将包含orderId的订单信息发送到Kafka。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreation(OrderCreatedEvent event) {
        log.info("接收到订单创建的事务后事件，准备发送到Kafka: orderId={}", event.getOrderId());
        try {
            KafkaOrderMessage message = new KafkaOrderMessage(event.getOrderId(), event.getOrderDto());
            String messagePayload = objectMapper.writeValueAsString(message);
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

    /**
     * 监听订单取消事件（在事务成功提交后），并将取消信息发送到Kafka。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCancellation(OrderCancelEvent event) {
        CancelOrderDto dto = event.getCancelOrderDto();
        log.info("接收到订单取消的事务后事件，准备发送到Kafka: orderId={}", dto.getOrderId());
        try {
            String messagePayload = objectMapper.writeValueAsString(dto);
            kafkaTemplate.send(TOPIC_CANCEL_ORDERS, messagePayload).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("发送取消订单消息到Kafka失败: orderId={}, error={}", dto.getOrderId(), ex.getMessage(), ex);
                } else {
                    log.info("成功发送取消订单消息到Kafka: orderId={}, topic={}, offset={}",
                            dto.getOrderId(),
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().offset());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("序列化CancelOrderDto为JSON时失败: orderId={}", dto.getOrderId(), e);
        }
    }
}
