package com.remus.dwanjcex.wallet.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Asset {
    private String symbol; // 资产代号，如 BTC, USDT (主键)
    private String name;   // 资产全称，如 Bitcoin
    private Integer scale; // 小数精度，如 8
    private Boolean isEnabled; // 是否启用
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
