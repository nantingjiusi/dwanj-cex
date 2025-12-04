package com.remus.dwanjcex.wallet.entity;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Trade {
    private Long id;
    private Long buyOrderId;
    private Long sellOrderId;
    private String symbol;     // BTC/USDT
    private BigDecimal price;
    private BigDecimal quantity;
    private LocalDateTime createdAt = LocalDateTime.now();
}
