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
public class LedgerLog {
    private Long id;
    private Long userId;
    private String assetSymbol;
    private String chain;
    private String bizType;
    private String bizId;
    private BigDecimal amount;
    private BigDecimal beforeAvailable;
    private BigDecimal beforeFrozen;
    private BigDecimal afterAvailable;
    private BigDecimal afterFrozen;
    private String remark;
    private LocalDateTime createdAt;
}
