package com.remus.dwanjcex.wallet.entity.state;

import com.remus.dwanjcex.common.OrderStatus;
import com.remus.dwanjcex.wallet.entity.OrderEntity;

import java.math.BigDecimal;

public class PartiallyFilledOrderState implements OrderState {

    public static final PartiallyFilledOrderState INSTANCE = new PartiallyFilledOrderState();

    private PartiallyFilledOrderState() {}

    @Override
    public OrderStatus getStatus() {
        return OrderStatus.PARTIALLY_FILLED;
    }

    @Override
    public OrderState onFill(OrderEntity order, BigDecimal filledQty, BigDecimal filledAmount) {
        order.addFilled(filledQty);
        order.addQuoteFilled(filledAmount);

        if (order.isFullyFilled()) {
            return FilledOrderState.INSTANCE;
        }
        // 状态不变
        return this;
    }

    @Override
    public OrderState onCancel(OrderEntity order) {
        // 部分成交后取消，变为 PARTIALLY_FILLED_AND_CLOSED
        return PartiallyFilledAndClosedOrderState.INSTANCE;
    }
}
