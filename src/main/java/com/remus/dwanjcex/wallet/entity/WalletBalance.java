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
public class WalletBalance {
    private Long id;
    private Long userId;
    private String assetSymbol;
    private String chain;
    private BigDecimal available;
    private BigDecimal frozen;
    private BigDecimal total;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
