package com.remus.dwanjcex.engine.stp;

import com.remus.dwanjcex.disruptor.event.DisruptorEvent;
import com.remus.dwanjcex.engine.OrderBook;
import com.remus.dwanjcex.wallet.entity.OrderEntity;

import java.util.Deque;

/**
 * 自成交保护 (Self-Trade Prevention, STP) 策略接口。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/19 20:00
 */
public interface STPStrategy {

    /**
     * 处理检测到的自成交情况。
     *
     * @param takerOrder  吃单订单 (新订单)
     * @param makerOrder  挂单订单 (订单簿中的订单)
     * @param orderBook   订单簿
     * @param makerQueue  挂单所在的队列
     * @param event       当前的Disruptor事件
     * @return 如果应该中断后续的撮合循环，则返回true。
     */
    boolean handleSelfTrade(OrderEntity takerOrder, OrderEntity makerOrder, OrderBook orderBook, Deque<OrderEntity> makerQueue, DisruptorEvent event);
}
