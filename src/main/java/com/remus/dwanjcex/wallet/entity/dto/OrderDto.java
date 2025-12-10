package com.remus.dwanjcex.wallet.entity.dto;

import com.remus.dwanjcex.common.OrderTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * OrderDto 用于前端下单请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDto {
    private Long userId;          // 用户ID
    private String symbol;        // 交易对，例如 BTCUSDT
    private BigDecimal price;     // 下单价格
    private BigDecimal amount;    // 下单数量
    private OrderTypes.Side side;      // 买卖方向：BUY 或 SELL


}