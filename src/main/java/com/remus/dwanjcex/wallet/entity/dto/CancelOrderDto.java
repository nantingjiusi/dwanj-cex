package com.remus.dwanjcex.wallet.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 取消订单请求的数据传输对象。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/18 16:05
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
}
