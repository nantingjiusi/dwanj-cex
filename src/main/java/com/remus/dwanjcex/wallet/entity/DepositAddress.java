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
public class DepositAddress {
    private Long id;
    private Long userId;
    private String assetSymbol;
    private String chain;
    private String address;
    private String tag;
    private LocalDateTime createdAt;
}
