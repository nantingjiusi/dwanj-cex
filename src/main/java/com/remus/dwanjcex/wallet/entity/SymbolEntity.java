package com.remus.dwanjcex.wallet.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易对实体
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/16 10:00
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SymbolEntity {

    /**
     * 主键
     */
    private Integer id;

    /**
     * 交易对名称，例如：BTCUSDT
     */
    private String symbol;

    /**
     * 基础货币，例如：BTC
     */
    private String baseCoin;

    /**
     * 计价货币，例如：USDT
     */
    private String quoteCoin;

    /**
     * 基础货币精度（下单数量的小数位数）
     */
    private Integer baseScale;

    /**
     * 计价货币精度（价格的小数位数）
     */
    private Integer quoteScale;

    /**
     * 【新增】最小订单额。
     * 例如，对于BTC/USDT，此值可能为10，表示订单总价值不能低于10 USDT。
     */
    private BigDecimal minOrderValue;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
