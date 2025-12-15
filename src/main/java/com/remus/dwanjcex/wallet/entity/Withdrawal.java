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
public class Withdrawal {
    private Long id;
    private String withdrawId;
    private Long userId;
    private String assetSymbol;
    private String chain;
    private String toAddress;
    private BigDecimal amount;
    private BigDecimal fee;
    private String status; // REQUESTED, PROCESSING, BROADCASTED, DONE, REJECTED
    private String txid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
