package com.remus.dwanjcex.wallet.entity.state;

import com.remus.dwanjcex.common.OrderStatus;
import com.remus.dwanjcex.wallet.entity.OrderEntity;

import java.math.BigDecimal;

/**
 * 订单状态接口 (State Pattern)
 * 定义了订单在不同状态下对各种事件的响应行为。
 */
public interface OrderState {

    /**
     * 获取当前状态的枚举值
     */
    OrderStatus getStatus();

    /**
     * 处理成交事件
     * @param order 订单实体
     * @param filledQty 本次成交数量
     * @param filledAmount 本次成交金额 (用于市价买单)
     * @return 新的状态对象 (如果状态发生变化)，或者 this (如果状态不变)
     */
    OrderState onFill(OrderEntity order, BigDecimal filledQty, BigDecimal filledAmount);

    /**
     * 处理取消事件
     * @param order 订单实体
     * @return 新的状态对象 (通常是 CanceledOrderState)
     */
    OrderState onCancel(OrderEntity order);
}
