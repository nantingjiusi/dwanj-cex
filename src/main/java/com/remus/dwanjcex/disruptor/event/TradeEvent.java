package com.remus.dwanjcex.disruptor.event;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TradeEvent {
    private String symbol;
    private BigDecimal price;
    private BigDecimal quantity;
    private Long takerOrderId;
    private Long makerOrderId;
    private Long takerUserId;
    private Long makerUserId;
}
