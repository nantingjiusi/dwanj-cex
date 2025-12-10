package com.remus.dwanjcex.disruptor.event;

import com.remus.dwanjcex.wallet.entity.dto.CancelOrderDto;
import com.remus.dwanjcex.wallet.entity.dto.OrderDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 在Disruptor环形缓冲区中流转的通用事件包装器。
 * 它可以携带不同类型的业务事件（如下单、取消等）。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/18 16:10
 */
@Data
public class DisruptorEvent {

    /**
     * 事件类型
     */
    private EventType type;

    /**
     * 下单请求的数据
     */
    private OrderDto placeOrder;

    /**
     * 取消订单请求的数据
     */
    private CancelOrderDto cancelOrder;

    /**
     * 在撮合过程中产生的成交事件列表。
     * 由MatchingHandler填充，由PersistenceHandler消费。
     */
    private List<TradeEvent> tradeEvents;

    /**
     * 用于Disruptor重置事件对象，避免GC
     */
    public void clear() {
        this.type = null;
        this.placeOrder = null;
        this.cancelOrder = null;
        if (this.tradeEvents != null) {
            this.tradeEvents.clear();
        }
    }

    /**
     * 添加一个成交事件。
     * @param tradeEvent 成交事件
     */
    public void addTradeEvent(TradeEvent tradeEvent) {
        if (this.tradeEvents == null) {
            this.tradeEvents = new ArrayList<>();
        }
        this.tradeEvents.add(tradeEvent);
    }
}
