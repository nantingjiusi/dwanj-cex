package com.remus.dwanjcex.wallet.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
     * 状态：1-上线，0-下线
     */
    private Integer status;
}
