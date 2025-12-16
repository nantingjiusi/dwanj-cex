package com.remus.dwanjcex.wallet.services;

import com.remus.dwanjcex.wallet.entity.Market;
import com.remus.dwanjcex.wallet.mapper.MarketMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MarketService {

    private final MarketMapper marketMapper;
    private final Map<String, Market> marketCache = new ConcurrentHashMap<>();

    public MarketService(MarketMapper marketMapper) {
        this.marketMapper = marketMapper;
    }

    @PostConstruct
    public void init() {
        log.info("开始加载交易对信息...");
        List<Market> markets = marketMapper.findAll();
        if (markets == null || markets.isEmpty()) {
            log.warn("数据库中未找到任何已上线的交易对。");
            return;
        }
        this.marketCache.putAll(markets.stream()
                .collect(Collectors.toMap(Market::getSymbol, Function.identity())));
        log.info("加载了 {} 个交易对信息。", markets.size());
    }

    public Market getMarket(String symbol) {
        return marketCache.get(symbol);
    }

    public Collection<Market> getAllMarkets() {
        return marketCache.values();
    }

    public void createMarket(Market market) {
        if (marketMapper.findBySymbol(market.getSymbol()) != null) {
            throw new IllegalStateException("Market with symbol " + market.getSymbol() + " already exists.");
        }
        marketMapper.insert(market);
        marketCache.put(market.getSymbol(), market);
        log.info("Market {} has been created and cached.", market.getSymbol());
    }
}
