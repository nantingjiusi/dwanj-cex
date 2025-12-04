package com.remus.dwanjcex.wallet.entity;

import com.remus.dwanjcex.common.OrderStatus;
import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.common.SymbolEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEntity {
    private Long id;
    private Long userId;
    private SymbolEnum symbol;        // BTC/USDT
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal filled = BigDecimal.ZERO;
    private OrderTypes.Side side; // BUY / SELL
    private OrderStatus status;   // NEW, PARTIAL, FILLED, CANCELED
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
    private Long version = 0L;   // 乐观锁字段


    /** 剩余未成交数量 */
    public BigDecimal getRemaining() {
        if (filled == null) return amount;
        return amount.subtract(filled);
    }

    /** 增加已成交数量 */
    public void addFilled(BigDecimal qty) {
        if (filled == null) filled = BigDecimal.ZERO;
        filled = filled.add(qty);
    }

    /** 判断订单是否完全成交 */
    public boolean isFullyFilled() {
        return filled != null && filled.compareTo(amount) >= 0;
    }
}
