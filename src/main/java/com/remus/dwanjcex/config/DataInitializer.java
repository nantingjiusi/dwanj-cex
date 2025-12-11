package com.remus.dwanjcex.config;

import com.remus.dwanjcex.wallet.entity.Asset;
import com.remus.dwanjcex.wallet.entity.SymbolEntity;
import com.remus.dwanjcex.wallet.services.AssetService;
import com.remus.dwanjcex.wallet.services.SymbolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Component
@Order(1) // 【新增】确保尽早执行
@RequiredArgsConstructor
public class DataInitializer  implements ApplicationRunner {

    private final AssetService assetService;
    private final SymbolService symbolService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 【新增】醒目的日志，确认是否被调用

        log.info("Initializing default data...");

        // 1. 初始化资产
        createAssetIfNotExists("BTC", "Bitcoin", 8, true);
        createAssetIfNotExists("USDT", "TetherUS", 6, true);
        createAssetIfNotExists("ETH", "Ethereum", 8, true);
        createAssetIfNotExists("ZEU", "Zeus Coin", 6, true);

        // 2. 初始化交易对
        createSymbolIfNotExists("BTCUSDT", "BTC", "USDT", 8, 2);
        createSymbolIfNotExists("ETHUSDT", "ETH", "USDT", 8, 2);
        createSymbolIfNotExists("ZEUUSDT", "ZEU", "USDT", 6, 4);

        log.info("Default data initialized.");
    }

    private void createAssetIfNotExists(String symbol, String name, int scale, boolean isEnabled) {
        if (assetService.getAsset(symbol) == null) {
            Asset asset = Asset.builder()
                    .symbol(symbol)
                    .name(name)
                    .scale(scale)
                    .isEnabled(isEnabled)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            assetService.createAsset(asset);
            log.info("Created asset: {}", symbol);
        }
    }

    private void createSymbolIfNotExists(String symbol, String baseCoin, String quoteCoin, int baseScale, int quoteScale) {
        if (symbolService.getSymbol(symbol) == null) {
            SymbolEntity symbolEntity = SymbolEntity.builder()
                    .symbol(symbol)
                    .baseCoin(baseCoin)
                    .quoteCoin(quoteCoin)
                    .baseScale(baseScale)
                    .quoteScale(quoteScale)
                    .minOrderValue(new BigDecimal("10"))
                    .build();
            symbolService.createSymbol(symbolEntity);
            log.info("Created symbol: {}", symbol);
        }
    }

}
