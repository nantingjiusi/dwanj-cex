package com.remus.dwanjcex.engine;

import com.remus.dwanjcex.common.SymbolEnum;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理多个交易对的订单簿
 */
@Component
public class MultiSymbolOrderBookManager {

    private final Map<SymbolEnum, OrderBook> books = new ConcurrentHashMap<>();

    /** 获取指定交易对订单簿，如果不存在则创建 */
    public OrderBook getOrderBook(SymbolEnum symbol) {
        return books.computeIfAbsent(symbol, OrderBook::new);
    }

    /** 删除交易对订单簿（可选） */
    public void removeOrderBook(SymbolEnum symbol) {
        books.remove(symbol);
    }

    /** 获取所有订单簿快照 */
    public Map<SymbolEnum, Map<String, Map<BigDecimal, List<OrderEntity>>>> getAllOrderBooksSnapshot() {
        Map<SymbolEnum, Map<String, Map<BigDecimal, List<OrderEntity>>>> snapshot = new ConcurrentHashMap<>();
        books.forEach((symbol, book) -> snapshot.put(symbol, book.getOrderBookSnapshot()));
        return snapshot;
    }
}
