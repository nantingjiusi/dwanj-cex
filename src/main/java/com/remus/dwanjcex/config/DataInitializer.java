package com.remus.dwanjcex.config;

import com.remus.dwanjcex.wallet.entity.Asset;
import com.remus.dwanjcex.wallet.entity.SymbolEntity;
import com.remus.dwanjcex.wallet.services.AssetService;
import com.remus.dwanjcex.wallet.services.SymbolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AssetService assetService;
    private final SymbolService symbolService;

    @Override
    public void run(String... args) throws Exception {
        log.info("Initializing default data...");

        // 1. 初始化资产
        createAssetIfNotExists("BTC", "Bitcoin", 8, "0.00001", "100");
        createAssetIfNotExists("USDT", "TetherUS", 6, "0.01", "1000000");
        createAssetIfNotExists("ETH", "Ethereum", 8, "0.0001", "1000");
        createAssetIfNotExists("ZEU", "Zeus Coin", 6, "1", "1000000");

        // 2. 初始化交易对
        createSymbolIfNotExists("BTCUSDT", "BTC", "USDT", 8, 2);
        createSymbolIfNotExists("ETHUSDT", "ETH", "USDT", 8, 2);
        createSymbolIfNotExists("ZEUUSDT", "ZEU", "USDT", 6, 4);

        log.info("Default data initialized.");
    }

    private void createAssetIfNotExists(String symbol, String displayName, int precision, String minSize, String maxSize) {
        if (assetService.getAsset(symbol) == null) {
            Asset asset = Asset.builder()
                    .symbol(symbol)
                    .displayName(displayName)
                    .precisionDigits(precision)
                    .minSize(new BigDecimal(minSize))
                    .maxSize(new BigDecimal(maxSize))
                    .isTradable(true)
                    .isDepositEnabled(true)
                    .isWithdrawEnabled(true)
                    .isEnabled(true)
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
