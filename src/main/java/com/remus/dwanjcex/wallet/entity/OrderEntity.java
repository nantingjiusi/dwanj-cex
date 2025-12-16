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
    private String marketSymbol;
    private OrderTypes.Side side;
    private OrderTypes.OrderType type;
    
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;
    
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ZERO;
    
    @Builder.Default
    private BigDecimal filled = BigDecimal.ZERO;
    
    // 【修复】这个字段应该在addFilled中计算，而不是直接持久化
    // private BigDecimal remaining; 
    
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 非数据库字段
    @Builder.Default
    private BigDecimal quoteAmount = BigDecimal.ZERO;
    
    @Builder.Default
    private BigDecimal quoteFilled = BigDecimal.ZERO;

    /**
     * 【关键修复】提供一个动态计算的getRemaining方法
     */
    public BigDecimal getRemaining() {
        if (this.quantity == null) return BigDecimal.ZERO;
        if (this.filled == null) return this.quantity;
        return this.quantity.subtract(this.filled);
    }

    public void addFilled(BigDecimal qty) {
        if (this.filled == null) this.filled = BigDecimal.ZERO;
        this.filled = this.filled.add(qty);
    }
    
    public void addQuoteFilled(BigDecimal quoteQty) {
        if (this.quoteFilled == null) this.quoteFilled = BigDecimal.ZERO;
        this.quoteFilled = this.quoteFilled.add(quoteQty);
    }

    public boolean isFullyFilled() {
        if (this.type == OrderTypes.OrderType.MARKET && this.side == OrderTypes.Side.BUY) {
            return this.quoteFilled != null && this.quoteAmount != null && this.quoteAmount.compareTo(BigDecimal.ZERO) > 0 && this.quoteFilled.compareTo(this.quoteAmount) >= 0;
        } else {
            return this.quantity != null && this.quantity.compareTo(BigDecimal.ZERO) > 0 && this.filled.compareTo(this.quantity) >= 0;
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
