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
public class AssetChain {
    private Long id;
    private String assetSymbol;
    private String chain;
    private String contractAddress;
    private BigDecimal minDeposit;
    private BigDecimal minWithdraw;
    private BigDecimal withdrawFee;
    private Integer confirmations;
    private Boolean isEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
