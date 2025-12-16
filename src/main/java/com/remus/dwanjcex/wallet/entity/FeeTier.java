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
public class FeeTier {
    private Integer id;
    private String tierName;
    private BigDecimal makerRate;
    private BigDecimal takerRate;
    private BigDecimal minVolume;
    private LocalDateTime createdAt;
}
