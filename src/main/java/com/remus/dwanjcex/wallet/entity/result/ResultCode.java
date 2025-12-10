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
    INSUFFICIENT_BASE(1005, "基础币不足"), SYMBOL_NOT_SUPPORTED(1007,"不支持该交易对" ),
    ORDER_NOT_BELONG_TO_USER(1006,"订单不属于该用户" ),
    ORDER_CANNOT_BE_CANCELED(1007,"订单无法取消" ),
    USER_NOT_FOUND(2001,"用户不存在" ),
    UNAUTHORIZED(2002,"用户无权限" ),
    INVALID_CREDENTIALS(2003,"密码错误" );

    private final int code;
    private final String message;
}
