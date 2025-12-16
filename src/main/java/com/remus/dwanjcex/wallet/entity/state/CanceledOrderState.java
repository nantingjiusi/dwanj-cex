package com.remus.dwanjcex.wallet.entity.state;

import com.remus.dwanjcex.common.OrderStatus;
import com.remus.dwanjcex.wallet.entity.OrderEntity;

import java.math.BigDecimal;

public class CanceledOrderState implements OrderState {

    public static final CanceledOrderState INSTANCE = new CanceledOrderState();

    private CanceledOrderState() {}

    @Override
    public OrderStatus getStatus() {
        return OrderStatus.CANCELED;
    }

    @Override
    public OrderState onFill(OrderEntity order, BigDecimal filledQty, BigDecimal filledAmount) {
        throw new IllegalStateException("Cannot fill a canceled order.");
    }

    @Override
    public OrderState onCancel(OrderEntity order) {
        // 已经是取消状态，无需变化
        return this;
    }
}
