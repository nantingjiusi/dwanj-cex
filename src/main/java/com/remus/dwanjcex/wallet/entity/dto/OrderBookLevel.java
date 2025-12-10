package com.remus.dwanjcex.wallet.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 订单簿深度等级的DTO。
 * 表示在某个价格上，聚合了多少数量。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/17 16:00
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderBookLevel {

    /**
     * 价格
     */
    private BigDecimal price;

    /**
     * 在该价格上聚合后的总数量（剩余数量）
     */
    private BigDecimal quantity;
}
