package com.remus.dwanjcex.engine;

import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.dto.OrderBookLevel;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

/**
 * 单个交易对订单簿
 */
public class OrderBook {

    private final ConcurrentSkipListMap<BigDecimal, Deque<OrderEntity>> bids =
            new ConcurrentSkipListMap<>(Comparator.reverseOrder());
    private final ConcurrentSkipListMap<BigDecimal, Deque<OrderEntity>> asks =
            new ConcurrentSkipListMap<>(BigDecimal::compareTo);

    @Getter
    private final String symbol;

    public OrderBook(String symbol) {
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

    /**
     * 获取订单簿的聚合深度快照。
     * 返回的是按价格聚合后的深度信息，而不是原始订单列表。
     *
     * @return 包含"bids"和"asks"的深度列表的Map
     */
    public Map<String, List<OrderBookLevel>> getOrderBookSnapshot() {
        Map<String, List<OrderBookLevel>> snapshot = new LinkedHashMap<>();

        List<OrderBookLevel> bidLevels = bids.entrySet().stream()
                .map(entry -> {
                    BigDecimal price = entry.getKey();
                    // 累加该价格下所有订单的剩余数量
                    BigDecimal totalQuantity = entry.getValue().stream()
                            .map(OrderEntity::getRemaining)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new OrderBookLevel(price, totalQuantity);
                })
                .filter(level -> level.getQuantity().compareTo(BigDecimal.ZERO) > 0) // 过滤掉数量为0的深度
                .collect(Collectors.toList());

        List<OrderBookLevel> askLevels = asks.entrySet().stream()
                .map(entry -> {
                    BigDecimal price = entry.getKey();
                    BigDecimal totalQuantity = entry.getValue().stream()
                            .map(OrderEntity::getRemaining)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new OrderBookLevel(price, totalQuantity);
                })
                .filter(level -> level.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        snapshot.put("bids", bidLevels);
        snapshot.put("asks", askLevels);
        return snapshot;
    }
}
