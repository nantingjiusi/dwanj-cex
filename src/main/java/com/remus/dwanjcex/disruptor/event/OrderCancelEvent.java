package com.remus.dwanjcex.disruptor.event;

import com.remus.dwanjcex.wallet.entity.dto.CancelOrderDto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Spring应用事件，在订单取消的事务成功提交后发布。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/18 17:00
 */
@Getter
public class OrderCancelEvent extends ApplicationEvent {

    private final CancelOrderDto cancelOrderDto;

    /**
     * @param source         事件源
     * @param cancelOrderDto 取消订单的DTO
     */
    public OrderCancelEvent(Object source, CancelOrderDto cancelOrderDto) {
        super(source);
        this.cancelOrderDto = cancelOrderDto;
    }
}
