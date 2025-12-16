package com.remus.dwanjcex.wallet.entity.state;

import com.remus.dwanjcex.common.OrderStatus;
import com.remus.dwanjcex.wallet.entity.OrderEntity;

import java.math.BigDecimal;

public class NewOrderState implements OrderState {

    public static final NewOrderState INSTANCE = new NewOrderState();

    private NewOrderState() {}

    @Override
    public OrderStatus getStatus() {
        return OrderStatus.NEW;
    }

    @Override
    public OrderState onFill(OrderEntity order, BigDecimal filledQty, BigDecimal filledAmount) {
        order.addFilled(filledQty);
        order.addQuoteFilled(filledAmount);

        if (order.isFullyFilled()) {
            return FilledOrderState.INSTANCE;
        }
        return PartiallyFilledOrderState.INSTANCE;
    }

    @Override
    public OrderState onCancel(OrderEntity order) {
        // 在NEW状态下取消，直接变为CANCELED
        return CanceledOrderState.INSTANCE;
    }
}
