package com.remus.dwanjcex.engine;

import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.dto.OrderBookLevel;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

    /**
     * 核心优化：维护一个从orderId到OrderEntity的直接映射，用于O(1)复杂度的快速查找和移除。
     */
    private final Map<Long, OrderEntity> orderMap = new ConcurrentHashMap<>();

    @Getter
    private final String symbol;

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    /**
     * 添加订单。
     * 同时更新价格队列和orderId映射。
     */
    public void add(OrderEntity order) {
        orderMap.put(order.getId(), order);

        ConcurrentSkipListMap<BigDecimal, Deque<OrderEntity>> book =
                order.getSide() == OrderTypes.Side.BUY ? bids : asks;
        book.compute(order.getPrice(), (price, deque) -> {
            if (deque == null) deque = new ConcurrentLinkedDeque<>();
            deque.addLast(order);
            return deque;
        });
    }

    /**
     * 根据订单ID快速移除订单 (O(1) 复杂度)。
     *
     * @param orderId 订单ID
     * @return 如果成功移除返回true
     */
    public boolean remove(Long orderId) {
        // 1. 从直接映射中快速找到订单
        OrderEntity order = orderMap.remove(orderId);
        if (order == null) {
            // 订单可能已经成交并被移除，这不是一个错误
            return false;
        }

        // 2. 根据订单信息，直接定位到价格队列
        ConcurrentSkipListMap<BigDecimal, Deque<OrderEntity>> book =
                order.getSide() == OrderTypes.Side.BUY ? bids : asks;
        Deque<OrderEntity> deque = book.get(order.getPrice());

        // 3. 从队列中移除
        if (deque != null) {
            deque.remove(order); // 直接移除对象，比removeIf更快
            if (deque.isEmpty()) {
                book.remove(order.getPrice());
            }
        }
        return true;
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
     */
    public Map<String, List<OrderBookLevel>> getOrderBookSnapshot() {
        Map<String, List<OrderBookLevel>> snapshot = new LinkedHashMap<>();

        List<OrderBookLevel> bidLevels = bids.entrySet().stream()
                .map(entry -> {
                    BigDecimal price = entry.getKey();
                    BigDecimal totalQuantity = entry.getValue().stream()
                            .map(OrderEntity::getRemaining)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new OrderBookLevel(price, totalQuantity);
                })
                .filter(level -> level.getQuantity().compareTo(BigDecimal.ZERO) > 0)
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
