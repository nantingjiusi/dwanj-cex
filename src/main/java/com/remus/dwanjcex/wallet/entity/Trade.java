package com.remus.dwanjcex.wallet.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade {
    private Long id;
    private String marketSymbol;
    private BigDecimal price;
    private BigDecimal quantity;
    private Long takerOrderId;
    private Long makerOrderId;
    private Long takerUserId;
    private Long makerUserId;
    private BigDecimal fee;
    private LocalDateTime createdAt;
}
