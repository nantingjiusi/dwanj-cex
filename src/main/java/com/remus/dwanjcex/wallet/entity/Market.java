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
public class Market {
    private Long id;
    private String symbol;
    private String baseAsset;
    private String quoteAsset;
    private Integer pricePrecision;
    private Integer quantityPrecision;
    private BigDecimal minOrderSize;
    private BigDecimal minOrderValue;
    private BigDecimal makerFeeRate;
    private BigDecimal takerFeeRate;
    private Boolean isEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
