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
    private Long userId;
    private String symbol;
    private OrderTypes.OrderType type;
    private OrderTypes.Side side;

    // 对于限价单，此字段必须提供。对于市价单，此字段将被忽略。
    private BigDecimal price;

    // 对于限价单和市价卖单，这是指基础货币的数量 (e.g., 多少个BTC)
    private BigDecimal amount;

    // 仅用于市价买单，指报价货币的金额 (e.g., 花多少USDT去买)
    private BigDecimal quoteAmount;
}
