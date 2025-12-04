package com.remus.dwanjcex.common;

import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public enum AssetEnum {
    BTC("BTC"),
    USDT("USDT"),
    ETH("ETH"),
    HOTPOT("HOTPOT"); // 示例自定义币

    private final String code;

    /** 根据字符串获取枚举 */
    public static AssetEnum fromString(String code) {
        for (AssetEnum asset : AssetEnum.values()) {
            if (asset.getCode().equalsIgnoreCase(code)) {
                return asset;
            }
        }
        throw new IllegalArgumentException("Unsupported asset: " + code);
    }
}