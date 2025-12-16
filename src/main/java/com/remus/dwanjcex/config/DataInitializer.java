package com.remus.dwanjcex.config;

import com.remus.dwanjcex.wallet.entity.Asset;
import com.remus.dwanjcex.wallet.entity.Market;
import com.remus.dwanjcex.wallet.services.AssetService;
import com.remus.dwanjcex.wallet.services.MarketService;
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
    private final MarketService marketService;

    @Override
    public void run(String... args) throws Exception {
        log.info("Initializing default data...");

        // 1. 初始化资产
        createAssetIfNotExists("BTC", "Bitcoin", 8, "0.00001", "100");
        createAssetIfNotExists("USDT", "TetherUS", 6, "0.01", "1000000");
        createAssetIfNotExists("ETH", "Ethereum", 8, "0.0001", "1000");
        createAssetIfNotExists("ZEU", "Zeus Coin", 6, "1", "1000000");

        // 2. 初始化交易对
        createMarketIfNotExists("BTCUSDT", "BTC", "USDT", 2, 8, "0.00001", "10");
        createMarketIfNotExists("ETHUSDT", "ETH", "USDT", 2, 8, "0.0001", "10");
        createMarketIfNotExists("ZEUUSDT", "ZEU", "USDT", 4, 6, "1", "10");

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

    private void createMarketIfNotExists(String symbol, String baseAsset, String quoteAsset, int pricePrecision, int quantityPrecision, String minOrderSize, String minOrderValue) {
        if (marketService.getMarket(symbol) == null) {
            Market market = Market.builder()
                    .symbol(symbol)
                    .baseAsset(baseAsset)
                    .quoteAsset(quoteAsset)
                    .pricePrecision(pricePrecision)
                    .quantityPrecision(quantityPrecision)
                    .minOrderSize(new BigDecimal(minOrderSize))
                    .minOrderValue(new BigDecimal(minOrderValue))
                    .isEnabled(true)
                    .build();
            marketService.createMarket(market);
            log.info("Created market: {}", symbol);
        }
    }
}
