package com.remus.dwanjcex.wallet.entity.state;

import com.remus.dwanjcex.common.OrderStatus;
import com.remus.dwanjcex.wallet.entity.OrderEntity;

import java.math.BigDecimal;

public class FilledOrderState implements OrderState {

    public static final FilledOrderState INSTANCE = new FilledOrderState();

    private FilledOrderState() {}

    @Override
    public OrderStatus getStatus() {
        return OrderStatus.FILLED;
    }

    @Override
    public OrderState onFill(OrderEntity order, BigDecimal filledQty, BigDecimal filledAmount) {
        throw new IllegalStateException("Cannot fill a fully filled order.");
    }

    @Override
    public OrderState onCancel(OrderEntity order) {
        throw new IllegalStateException("Cannot cancel a fully filled order.");
    }
}
