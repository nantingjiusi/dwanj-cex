package com.remus.dwanjcex.kafka.dto;

import com.remus.dwanjcex.wallet.entity.dto.OrderDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka消息体，用于封装订单ID和订单DTO，确保orderId能可靠传递。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/18 11:00
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KafkaOrderMessage {

    /**
     * 订单在数据库中的ID
     */
    private Long orderId;

    /**
     * 订单的详细信息DTO
     */
    private OrderDto orderDto;
}
