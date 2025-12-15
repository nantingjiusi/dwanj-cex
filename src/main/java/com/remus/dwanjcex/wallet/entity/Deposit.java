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
public class Deposit {
    private Long id;
    private String txid;
    private Long userId;
    private String assetSymbol;
    private String chain;
    private BigDecimal amount;
    private Integer confirmations;
    private String status; // PENDING, CONFIRMED, REJECTED
    private String memo;
    private LocalDateTime seenAt;
    private LocalDateTime createdAt;
}
