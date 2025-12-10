package com.remus.dwanjcex.common;

public class OrderTypes {
    public enum Side {
        BUY,
        SELL
    }

    /**
     * 订单类型枚举
     */
    public enum OrderType {
        /**
         * 限价单
         */
        LIMIT,
        /**
         * 市价单
         */
        MARKET
    }
}
