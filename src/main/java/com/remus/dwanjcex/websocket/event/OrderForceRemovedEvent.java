package com.remus.dwanjcex.websocket.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 订单被强制从内存中移除的事件。
 * 当持久化层发现一个订单因STP等原因被取消，但撮合层可能仍持有其引用时，
 * 发布此事件以强制清理撮合层的内存状态。
 */
@Getter
public class OrderForceRemovedEvent extends ApplicationEvent {

    private final String symbol;
    private final Long orderId;

    public OrderForceRemovedEvent(Object source, String symbol, Long orderId) {
        super(source);
        this.symbol = symbol;
        this.orderId = orderId;
    }
}
