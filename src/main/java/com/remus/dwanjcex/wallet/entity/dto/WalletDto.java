package com.remus.dwanjcex.wallet.entity.dto;


import com.remus.dwanjcex.common.AssetEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * WalletDto 用于前端请求钱包操作（充值、冻结、解冻等）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletDto {
    private Long userId;        // 用户ID
    private AssetEnum asset;       // 资产名称，例如 BTC、USDT
    private BigDecimal amount;  // 金额
    private String ref;         // 参考ID，例如订单ID或交易ID
}