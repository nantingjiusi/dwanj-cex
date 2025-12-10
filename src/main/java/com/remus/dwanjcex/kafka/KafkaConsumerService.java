package com.remus.dwanjcex.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remus.dwanjcex.disruptor.service.DisruptorService;
import com.remus.dwanjcex.kafka.dto.KafkaOrderMessage;
import com.remus.dwanjcex.wallet.entity.dto.CancelOrderDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Kafka消费者服务。
 * 负责从Kafka消费消息，并将其分发到系统内部进行处理。
 */
@Slf4j
@Service
public class KafkaConsumerService {

    private static final String TOPIC_ORDERS = "orders-topic";
    private static final String TOPIC_CANCEL_ORDERS = "cancel-orders-topic";
    private static final String GROUP_ID = "matching-engine-group";

    private final ObjectMapper objectMapper;
    private final DisruptorService disruptorService;

    public KafkaConsumerService(ObjectMapper objectMapper, DisruptorService disruptorService) {
        this.objectMapper = objectMapper;
        this.disruptorService = disruptorService;
    }

    /**
     * 监听订单主题，消费订单消息并送入Disruptor处理。
     */
    @KafkaListener(topics = TOPIC_ORDERS, groupId = GROUP_ID)
    public void listenOrderTopic(String message) {
        log.info("从Kafka接收到下单消息: {}", message);
        try {
            KafkaOrderMessage kafkaMessage = objectMapper.readValue(message, KafkaOrderMessage.class);
            // 将orderId和DTO分别传递
            disruptorService.publishPlaceOrderEvent(kafkaMessage.getOrderId(), kafkaMessage.getOrderDto());
        } catch (JsonProcessingException e) {
            log.error("反序列化下单消息为KafkaOrderMessage时失败: {}", message, e);
        }
    }

    /**
     * 监听取消订单主题，消费取消消息并送入Disruptor处理。
     */
    @KafkaListener(topics = TOPIC_CANCEL_ORDERS, groupId = GROUP_ID)
    public void listenCancelOrderTopic(String message) {
        log.info("从Kafka接收到取消订单消息: {}", message);
        try {
            CancelOrderDto cancelDto = objectMapper.readValue(message, CancelOrderDto.class);
            disruptorService.publishCancelOrderEvent(cancelDto);
        } catch (JsonProcessingException e) {
            log.error("反序列化取消消息为CancelOrderDto时失败: {}", message, e);
        }
    }
}
