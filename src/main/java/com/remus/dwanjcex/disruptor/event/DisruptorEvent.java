package com.remus.dwanjcex.disruptor.event;

import com.remus.dwanjcex.wallet.entity.dto.CancelOrderDto;
import com.remus.dwanjcex.wallet.entity.dto.OrderDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 在Disruptor环形缓冲区中流转的通用事件包装器。
 */
@Data
public class DisruptorEvent {

    private EventType type;

    // PLACE_ORDER 事件相关字段
    private Long orderId; // 数据库中的订单ID
    private OrderDto placeOrder;

    // CANCEL_ORDER 事件相关字段
    private CancelOrderDto cancelOrder;

    // 撮合结果
    private List<TradeEvent> tradeEvents;

    public void clear() {
        this.type = null;
        this.orderId = null;
        this.placeOrder = null;
        this.cancelOrder = null;
        if (this.tradeEvents != null) {
            this.tradeEvents.clear();
        }
    }

    public void addTradeEvent(TradeEvent tradeEvent) {
        if (this.tradeEvents == null) {
            this.tradeEvents = new ArrayList<>();
        }
        this.tradeEvents.add(tradeEvent);
    }
}
