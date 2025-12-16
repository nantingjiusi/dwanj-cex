package com.remus.dwanjcex.wallet.entity;

import com.remus.dwanjcex.exception.BusinessException;
import com.remus.dwanjcex.wallet.entity.result.ResultCode;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class WalletBalance {
    private Long id;
    private Long userId;
    private String assetSymbol;
    private String chain;
    
    @Setter(AccessLevel.PRIVATE)
    private BigDecimal available;
    
    @Setter(AccessLevel.PRIVATE)
    private BigDecimal frozen;
    
    @Setter(AccessLevel.PRIVATE)
    private BigDecimal total;
    
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 入账 (增加可用余额)
     */
    public void credit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Credit amount cannot be negative.");
        }
        this.available = this.available.add(amount);
        this.total = this.available.add(this.frozen);
    }

    /**
     * 出账 (扣减可用余额)
     */
    public void debit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Debit amount cannot be negative.");
        }
        if (this.available.compareTo(amount) < 0) {
            throw new BusinessException(ResultCode.INSUFFICIENT_BALANCE);
        }
        this.available = this.available.subtract(amount);
        this.total = this.available.add(this.frozen);
    }

    /**
     * 冻结
     */
    public void freeze(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Freeze amount cannot be negative.");
        }
        if (this.available.compareTo(amount) < 0) {
            throw new BusinessException(ResultCode.INSUFFICIENT_BALANCE);
        }
        this.available = this.available.subtract(amount);
        this.frozen = this.frozen.add(amount);
    }

    /**
     * 解冻
     */
    public void unfreeze(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Unfreeze amount cannot be negative.");
        }
        if (this.frozen.compareTo(amount) < 0) {
            throw new BusinessException(ResultCode.NO_FROZEN);
        }
        this.frozen = this.frozen.subtract(amount);
        this.available = this.available.add(amount);
    }

    /**
     * 扣减冻结
     */
    public void reduceFrozen(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Reduce frozen amount cannot be negative.");
        }
        if (this.frozen.compareTo(amount) < 0) {
            throw new BusinessException(ResultCode.NO_FROZEN);
        }
        this.frozen = this.frozen.subtract(amount);
        this.total = this.available.add(this.frozen);
    }
}
