package com.remus.dwanjcex.wallet.services;

import com.remus.dwanjcex.wallet.entity.SymbolEntity;
import com.remus.dwanjcex.wallet.mapper.SymbolMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 交易对管理服务
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/16 11:10
 */
@Slf4j
@Service
public class SymbolService {

    private final SymbolMapper symbolMapper;

    /**
     * 缓存所有交易对信息，以交易对名称（如 BTCUSDT）为key
     */
    private final Map<String, SymbolEntity> symbolCache = new ConcurrentHashMap<>();

    public SymbolService(SymbolMapper symbolMapper) {
        this.symbolMapper = symbolMapper;
    }

    /**
     * 在服务启动时，从数据库加载所有已上线的交易对到缓存中
     */
    @PostConstruct
    public void init() {
        log.info("开始加载交易对信息...");
        List<SymbolEntity> symbols = symbolMapper.findAll();
        if (symbols == null || symbols.isEmpty()) {
            log.warn("数据库中未找到任何已上线的交易对。");
            return;
        }
        this.symbolCache.putAll(symbols.stream()
                .collect(Collectors.toMap(SymbolEntity::getSymbol, Function.identity())));
        log.info("加载了 {} 个交易对信息。", symbols.size());
        symbols.forEach(i-> System.out.println(i.getSymbol()));
    }

    /**
     * 根据交易对名称获取交易对实体
     *
     * @param symbol 交易对名称, e.g., "BTC/USDT"
     * @return 交易对实体，如果不存在则返回 null
     */
    public SymbolEntity getSymbol(String symbol) {
        return symbolCache.get(symbol);
    }

    /**
     * 获取所有已缓存的交易对
     *
     * @return 所有交易对实体
     */
    public Collection<SymbolEntity> getAllSymbols() {
        return symbolCache.values();
    }

    /**
     * 检查交易对是否存在且已上线
     *
     * @param symbol 交易对名称
     * @return 如果存在且已上线则返回 true
     */
    public boolean isSymbolSupported(String symbol) {
        return symbolCache.containsKey(symbol);
    }
}
