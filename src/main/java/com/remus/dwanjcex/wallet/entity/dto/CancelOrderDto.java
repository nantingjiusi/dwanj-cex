package com.remus.dwanjcex.wallet.entity.dto;

import com.remus.dwanjcex.common.OrderTypes;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 取消订单请求的数据传输对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelOrderDto {

    /**
     * 要取消的订单ID
     */
    private Long orderId;

    /**
     * 发起取消请求的用户ID
     */
    private Long userId;

    /**
     * 交易对，用于快速定位订单簿
     */
    private String symbol;

    /**
     * 订单的买卖方向，用于快速定位订单簿中的具体位置
     */
    private OrderTypes.Side side;
}
