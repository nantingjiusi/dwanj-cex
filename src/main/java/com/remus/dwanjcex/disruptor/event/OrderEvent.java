package com.remus.dwanjcex.disruptor.event;

import com.remus.dwanjcex.common.OrderTypes;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 在Disruptor环形缓冲区中传递的订单事件。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/17 10:00
 */
@Data
public class OrderEvent {

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 交易对名称, e.g., "BTCUSDT"
     */
    private String symbol;

    /**
     * 价格
     */
    private BigDecimal price;

    /**
     * 数量
     */
    private BigDecimal amount;

    /**
     * 买卖方向
     */
    private OrderTypes.Side side;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 在撮合过程中产生的成交事件列表。
     * 由MatchingHandler填充，由PersistenceHandler消费。
     */
    private List<TradeEvent> tradeEvents;

    /**
     * 用于Disruptor重置事件对象，避免GC
     */
    public void clear() {
        this.orderId = null;
        this.symbol = null;
        this.price = null;
        this.amount = null;
        this.side = null;
        this.userId = null;
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
