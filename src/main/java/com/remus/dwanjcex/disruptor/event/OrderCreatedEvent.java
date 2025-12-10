package com.remus.dwanjcex.disruptor.event;

import com.remus.dwanjcex.wallet.entity.dto.OrderDto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Spring应用事件，在订单创建的事务成功提交后发布。
 * 用于解耦HTTP线程的事务与Disruptor的异步处理。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/17 18:00
 */
@Getter
public class OrderCreatedEvent extends ApplicationEvent {

    private final Long orderId;
    private final OrderDto orderDto;

    /**
     * @param source   事件源，通常是this
     * @param orderId  已创建的订单ID
     * @param orderDto 原始的订单请求DTO
     */
    public OrderCreatedEvent(Object source, Long orderId, OrderDto orderDto) {
        super(source);
        this.orderId = orderId;
        this.orderDto = orderDto;
    }
}
