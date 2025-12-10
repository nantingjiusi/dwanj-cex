package com.remus.dwanjcex.wallet.entity;

import com.remus.dwanjcex.common.LedgerTxType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerTx {
    private Long id;
    private Long userId;
    private String asset;      // BTC, USDT
    private BigDecimal amount;
    private LedgerTxType type; // DEPOSIT, FREEZE, UNFREEZE, SETTLE_DEBIT, SETTLE_CREDIT
    private String ref;        // 关联单号，如订单ID
    private LocalDateTime createdAt;
}
