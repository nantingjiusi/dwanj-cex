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
    }

    public SymbolEntity getSymbol(String symbol) {
        return symbolCache.get(symbol);
    }

    public Collection<SymbolEntity> getAllSymbols() {
        return symbolCache.values();
    }

    public boolean isSymbolSupported(String symbol) {
        return symbolCache.containsKey(symbol);
    }

    /**
     * 【新增】创建新的交易对，并更新缓存
     */
    public void createSymbol(SymbolEntity symbol) {
        if (symbolMapper.findBySymbol(symbol.getSymbol()) != null) {
            throw new IllegalStateException("Symbol with name " + symbol.getSymbol() + " already exists.");
        }
        symbolMapper.insert(symbol);
        symbolCache.put(symbol.getSymbol(), symbol); // 更新缓存
        log.info("Symbol {} has been created and cached.", symbol.getSymbol());
    }
}
