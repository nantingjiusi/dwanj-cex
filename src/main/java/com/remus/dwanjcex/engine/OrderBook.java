package com.remus.dwanjcex.engine;

import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.common.SymbolEnum;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * 单个交易对订单簿
 */
public class OrderBook {

    private final ConcurrentSkipListMap<BigDecimal, Deque<OrderEntity>> bids =
            new ConcurrentSkipListMap<>(Comparator.reverseOrder());
    private final ConcurrentSkipListMap<BigDecimal, Deque<OrderEntity>> asks =
            new ConcurrentSkipListMap<>(BigDecimal::compareTo);

    @Getter
    private final SymbolEnum symbol;

    public OrderBook(SymbolEnum symbol) {
        this.symbol = symbol;
    }

    /** 添加订单 */
    public void add(OrderEntity order) {
        ConcurrentSkipListMap<BigDecimal, Deque<OrderEntity>> book =
                order.getSide() == OrderTypes.Side.BUY ? bids : asks;
        book.compute(order.getPrice(), (price, deque) -> {
            if (deque == null) deque = new ConcurrentLinkedDeque<>();
            deque.addLast(order);
            return deque;
        });
    }

    /** 移除订单 */
    public void remove(OrderEntity order) {
        ConcurrentSkipListMap<BigDecimal, Deque<OrderEntity>> book =
                order.getSide() == OrderTypes.Side.BUY ? bids : asks;
        Deque<OrderEntity> deque = book.get(order.getPrice());
        if (deque != null) {
            deque.removeIf(o -> o.getId().equals(order.getId()));
            if (deque.isEmpty()) book.remove(order.getPrice());
        }
    }

    /** 获取买一价 */
    public Optional<Map.Entry<BigDecimal, Deque<OrderEntity>>> bestBid() {
        return bids.isEmpty() ? Optional.empty() : Optional.of(bids.firstEntry());
    }

    /** 获取卖一价 */
    public Optional<Map.Entry<BigDecimal, Deque<OrderEntity>>> bestAsk() {
        return asks.isEmpty() ? Optional.empty() : Optional.of(asks.firstEntry());
    }

    /** 获取订单簿快照 */
    public Map<String, Map<BigDecimal, List<OrderEntity>>> getOrderBookSnapshot() {
        Map<BigDecimal, List<OrderEntity>> bidSnapshot = bids.entrySet().stream()
                .collect(LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), new ArrayList<>(e.getValue())),
                        LinkedHashMap::putAll);

        Map<BigDecimal, List<OrderEntity>> askSnapshot = asks.entrySet().stream()
                .collect(LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), new ArrayList<>(e.getValue())),
                        LinkedHashMap::putAll);

        Map<String, Map<BigDecimal, List<OrderEntity>>> snapshot = new LinkedHashMap<>();
        snapshot.put("bids", bidSnapshot);
        snapshot.put("asks", askSnapshot);
        return snapshot;
    }
}
