package com.remus.dwanjcex.wallet.entity.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {
    SUCCESS(200, "成功"),
    ERROR(-1, "错误"),
    INSUFFICIENT_BALANCE(1001, "余额不足"),
    NO_FROZEN(1002, "没有冻结余额"),
    ORDER_NOT_FOUND(1003, "订单未找到"),
    INSUFFICIENT_QUOTE(1004, "报价币不足"),
    INSUFFICIENT_BASE(1005, "基础币不足");

    private final int code;
    private final String message;
}
