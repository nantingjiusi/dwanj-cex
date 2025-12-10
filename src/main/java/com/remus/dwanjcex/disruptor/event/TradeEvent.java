package com.remus.dwanjcex.disruptor.event;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 成交事件，用于在Disruptor处理器之间传递一笔已完成的交易信息。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/17 17:00
 */
@Data
@Builder
public class TradeEvent {

    /**
     * 交易对
     */
    private String symbol;

    /**
     * 成交价格
     */
    private BigDecimal price;

    /**
     * 成交数量
     */
    private BigDecimal quantity;

    /**
     * 买方订单ID
     */
    private Long buyOrderId;

    /**
     * 卖方订单ID
     */
    private Long sellOrderId;

    /**
     * 买方用户ID
     */
    private Long buyerUserId;

    /**
     * 卖方用户ID
     */
    private Long sellerUserId;

}
