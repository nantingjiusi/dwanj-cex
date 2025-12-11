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
    private Long orderId;
    private OrderDto placeOrder;

    // CANCEL_ORDER 事件相关字段
    private CancelOrderDto cancelOrder;

    // 撮合结果
    private List<TradeEvent> tradeEvents;

    /**
     * 自成交取消标志 (Cancel-Newest)。
     * 如果在撮合时发现新订单会与自己的订单成交，则将此标志设为true。
     * PersistenceHandler会根据此标志来取消新订单并解冻资金。
     */
    private boolean selfTradeCancel = false;

    /**
     * 在撮合过程中因自成交策略 (Cancel-Oldest) 而被取消的挂单ID列表。
     */
    private List<Long> cancelledOrderIds;

    public void clear() {
        this.type = null;
        this.orderId = null;
        this.placeOrder = null;
        this.cancelOrder = null;
        this.selfTradeCancel = false;
        if (this.tradeEvents != null) {
            this.tradeEvents.clear();
        }
        if (this.cancelledOrderIds != null) {
            this.cancelledOrderIds.clear();
        }
    }

    public void addTradeEvent(TradeEvent tradeEvent) {
        if (this.tradeEvents == null) {
            this.tradeEvents = new ArrayList<>();
        }
        this.tradeEvents.add(tradeEvent);
    }

    public void addCancelledOrderId(Long orderId) {
        if (this.cancelledOrderIds == null) {
            this.cancelledOrderIds = new ArrayList<>();
        }
        this.cancelledOrderIds.add(orderId);
    }
}
