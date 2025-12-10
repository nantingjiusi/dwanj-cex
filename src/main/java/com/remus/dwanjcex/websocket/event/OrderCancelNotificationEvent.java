package com.remus.dwanjcex.websocket.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 订单取消的通知事件。
 * 当一个订单因自成交或其他原因被系统取消时，由PersistenceHandler发布。
 * 用于通知WebSocket服务向特定用户推送消息。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/19 10:00
 */
@Getter
public class OrderCancelNotificationEvent extends ApplicationEvent {

    private final Long userId;
    private final Long orderId;
    private final String reason;

    /**
     * @param source  事件源
     * @param userId  订单所属的用户ID
     * @param orderId 被取消的订单ID
     * @param reason  取消原因
     */
    public OrderCancelNotificationEvent(Object source, Long userId, Long orderId, String reason) {
        super(source);
        this.userId = userId;
        this.orderId = orderId;
        this.reason = reason;
    }
}
