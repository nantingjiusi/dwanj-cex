package com.remus.dwanjcex.disruptor.event;

/**
 * 定义在Disruptor中流转的事件类型。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/18 16:00
 */
public enum EventType {
    /**
     * 下单请求事件
     */
    PLACE_ORDER,

    /**
     * 取消订单请求事件
     */
    CANCEL_ORDER
}
