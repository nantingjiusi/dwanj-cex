package com.remus.dwanjcex.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.dto.OrderBookLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

/**
 * 订单簿数据容器。
 * 只负责存储和基本操作订单数据，不包含任何撮合逻辑。
 */
@Slf4j
@Getter
@Setter
@NoArgsConstructor // 为Jackson提供无参数构造函数
@JsonIgnoreProperties(ignoreUnknown = true) // 忽略未知字段，增加健壮性
public class OrderBook {

    private ConcurrentSkipListMap<BigDecimal, Deque<OrderEntity>> bids = new ConcurrentSkipListMap<>(Comparator.reverseOrder());
    private ConcurrentSkipListMap<BigDecimal, Deque<OrderEntity>> asks = new ConcurrentSkipListMap<>(BigDecimal::compareTo);
    private Map<Long, OrderEntity> orderMap = new ConcurrentHashMap<>();
    private String symbol;

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public void add(OrderEntity order) {
        orderMap.put(order.getId(), order);
        ConcurrentSkipListMap<BigDecimal, Deque<OrderEntity>> book = order.getSide() == OrderTypes.Side.BUY ? bids : asks;
        book.compute(order.getPrice(), (price, deque) -> {
            if (deque == null) deque = new ConcurrentLinkedDeque<>();
            deque.addLast(order);
            return deque;
        });
    }

    public boolean remove(Long orderId) {
        OrderEntity order = orderMap.remove(orderId);
        if (order == null) return false;
        ConcurrentSkipListMap<BigDecimal, Deque<OrderEntity>> book = order.getSide() == OrderTypes.Side.BUY ? bids : asks;
        Deque<OrderEntity> deque = book.get(order.getPrice());
        if (deque != null) {
            deque.remove(order);
            if (deque.isEmpty()) {
                book.remove(order.getPrice());
            }
        }
        return true;
    }

    public void removePriceLevelIfEmpty(OrderTypes.Side side, BigDecimal price) {
        ConcurrentSkipListMap<BigDecimal, Deque<OrderEntity>> book = side == OrderTypes.Side.BUY ? bids : asks;
        Deque<OrderEntity> deque = book.get(price);
        if (deque != null && deque.isEmpty()) book.remove(price);
    }

    public Optional<Map.Entry<BigDecimal, Deque<OrderEntity>>> bestBid() { return bids.isEmpty() ? Optional.empty() : Optional.of(bids.firstEntry()); }
    public Optional<Map.Entry<BigDecimal, Deque<OrderEntity>>> bestAsk() { return asks.isEmpty() ? Optional.empty() : Optional.of(asks.firstEntry()); }

    public Map<String, List<OrderBookLevel>> getOrderBookSnapshot() {
        Map<String, List<OrderBookLevel>> snapshot = new LinkedHashMap<>();
        snapshot.put("bids", getLevels(bids));
        snapshot.put("asks", getLevels(asks));
        return snapshot;
    }

    private List<OrderBookLevel> getLevels(Map<BigDecimal, Deque<OrderEntity>> book) {
        return book.entrySet().stream()
                .map(entry -> new OrderBookLevel(entry.getKey(), entry.getValue().stream().map(OrderEntity::getRemaining).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .filter(level -> level.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
    }
}
