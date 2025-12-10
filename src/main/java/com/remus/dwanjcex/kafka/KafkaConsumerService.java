package com.remus.dwanjcex.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remus.dwanjcex.disruptor.service.DisruptorService;
import com.remus.dwanjcex.kafka.dto.KafkaOrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Kafka消费者服务。
 * 负责从Kafka消费消息，并将其分发到系统内部进行处理。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/18 10:15
 */
@Slf4j
@Service
public class KafkaConsumerService {

    private static final String TOPIC_ORDERS = "orders-topic";

    private final ObjectMapper objectMapper;
    private final DisruptorService disruptorService;

    public KafkaConsumerService(ObjectMapper objectMapper, DisruptorService disruptorService /*, OrderMapper orderMapper*/) {
        this.objectMapper = objectMapper;
        this.disruptorService = disruptorService;
    }

    /**
     * 监听订单主题，消费订单消息并送入Disruptor处理。
     *
     * @param message 消息负载，为KafkaOrderMessage的JSON字符串
     */
    @KafkaListener(topics = TOPIC_ORDERS, groupId = "matching-engine-group")
    public void listenOrderTopic(String message) {
        log.info("从Kafka接收到订单消息: {}", message);
        try {
            // 反序列化为KafkaOrderMessage
            KafkaOrderMessage kafkaMessage = objectMapper.readValue(message, KafkaOrderMessage.class);

            Long orderId = kafkaMessage.getOrderId();
            // OrderDto orderDto = kafkaMessage.getOrderDto(); // 如果需要，可以获取

            // 将订单发布到Disruptor
            disruptorService.publishToRingBuffer(orderId, kafkaMessage.getOrderDto());

        } catch (JsonProcessingException e) {
            log.error("反序列化Kafka消息为KafkaOrderMessage时失败: {}", message, e);
        }
    }

    // findOrderIdByDto 方法不再需要，可以移除
}
