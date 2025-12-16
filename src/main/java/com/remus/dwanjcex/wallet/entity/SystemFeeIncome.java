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
public class SystemFeeIncome {
    private Long id;
    private String assetSymbol;
    private BigDecimal amount;
    private Long tradeId;
    private Long userId;
    private String feeType; // MAKER / TAKER
    private LocalDateTime createdAt;
}
