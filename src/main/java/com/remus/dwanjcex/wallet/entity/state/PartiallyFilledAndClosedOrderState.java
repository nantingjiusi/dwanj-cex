package com.remus.dwanjcex.wallet.entity.state;

import com.remus.dwanjcex.common.OrderStatus;
import com.remus.dwanjcex.wallet.entity.OrderEntity;

import java.math.BigDecimal;

public class PartiallyFilledAndClosedOrderState implements OrderState {

    public static final PartiallyFilledAndClosedOrderState INSTANCE = new PartiallyFilledAndClosedOrderState();

    private PartiallyFilledAndClosedOrderState() {}

    @Override
    public OrderStatus getStatus() {
        return OrderStatus.PARTIALLY_FILLED_AND_CLOSED;
    }

    @Override
    public OrderState onFill(OrderEntity order, BigDecimal filledQty, BigDecimal filledAmount) {
        throw new IllegalStateException("Cannot fill a closed order.");
    }

    @Override
    public OrderState onCancel(OrderEntity order) {
        throw new IllegalStateException("Cannot cancel a closed order.");
    }
}
