package com.remus.dwanjcex.wallet.entity;

import com.remus.dwanjcex.common.OrderStatus;
import com.remus.dwanjcex.common.OrderTypes;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 订单实体。
 * 注意：移除了@Data注解，并手动实现了equals()和hashCode()，
 * 以确保对象的身份仅由其ID决定，不受其他可变字段的影响。
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEntity {
    private Long id;
    private Long userId;
    private String symbol;        // e.g., BTCUSDT
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
        return filled != null && amount != null && filled.compareTo(amount) >= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderEntity that = (OrderEntity) o;
        // 实体对象的身份只由ID决定
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        // 实体对象的哈希码只由ID决定
        return Objects.hash(id);
    }
}
