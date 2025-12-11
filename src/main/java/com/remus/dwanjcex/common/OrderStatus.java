package com.remus.dwanjcex.common;

public enum OrderStatus {
    NEW,
    PARTIAL,
    FILLED,
    CANCELED,
    /**
     * 部分成交并关闭。
     * 通常用于因深度不足而提前结束的市价单。
     */
    PARTIALLY_FILLED_AND_CLOSED
}
