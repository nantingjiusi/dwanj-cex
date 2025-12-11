package com.remus.dwanjcex.wallet.entity;

import com.remus.dwanjcex.common.OrderStatus;
import com.remus.dwanjcex.common.OrderTypes;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEntity {
    private Long id;
    private Long userId;
    private String symbol;
    private OrderTypes.OrderType type;
    private OrderTypes.Side side;

    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;

    // 【关键修复】为可能为null的字段提供默认值，以满足数据库NOT NULL约束
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal quoteAmount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal filled = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal quoteFilled = BigDecimal.ZERO;

    private OrderStatus status;
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    @Builder.Default
    private Long version = 0L;

    public BigDecimal getRemaining() {
        if (type == OrderTypes.OrderType.MARKET && side == OrderTypes.Side.BUY) {
            // 市价买单的剩余是金额
            if (quoteFilled == null) return quoteAmount;
            return quoteAmount.subtract(quoteFilled);
        } else {
            // 其他订单的剩余是数量
            if (filled == null) return amount;
            return amount.subtract(filled);
        }
    }

    public void addFilled(BigDecimal qty) {
        if (filled == null) filled = BigDecimal.ZERO;
        filled = filled.add(qty);
    }
    
    public void addQuoteFilled(BigDecimal quoteQty) {
        if (quoteFilled == null) quoteFilled = BigDecimal.ZERO;
        quoteFilled = quoteFilled.add(quoteQty);
    }

    public boolean isFullyFilled() {
        if (type == OrderTypes.OrderType.MARKET && side == OrderTypes.Side.BUY) {
            return quoteFilled != null && quoteAmount != null && quoteFilled.compareTo(quoteAmount) >= 0;
        } else {
            return filled != null && amount != null && amount.compareTo(BigDecimal.ZERO) > 0 && filled.compareTo(amount) >= 0;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderEntity that = (OrderEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
