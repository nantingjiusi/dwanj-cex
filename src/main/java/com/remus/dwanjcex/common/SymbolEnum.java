//package com.remus.dwanjcex.common;
//
//import lombok.AllArgsConstructor;
//import lombok.Getter;
//
//@Getter
//@AllArgsConstructor
//public enum SymbolEnum {
//    BTC_USDT(AssetEnum.BTC, AssetEnum.USDT),
//    ETH_USDT(AssetEnum.ETH, AssetEnum.USDT),
//    ETH_BTC(AssetEnum.ETH, AssetEnum.BTC);
//
//    private final AssetEnum baseCoin;   // 基础币
//    private final AssetEnum quoteCoin;  // 报价币
//
//    /** 获取基础币 */
//    public AssetEnum getBaseCoin() {
//        return baseCoin;
//    }
//
//    /** 获取报价币 */
//    public AssetEnum getQuoteCoin() {
//        return quoteCoin;
//    }
//
//    /** 根据字符串生成 SymbolEnum，例如 "BTC/USDT" -> BTC_USDT */
//    public static SymbolEnum fromString(String symbol) {
//        return SymbolEnum.valueOf(symbol.replace("/", "_"));
//    }
//
//    public String getSymbol(){
//        return baseCoin+"/"+quoteCoin;
//    }
//}