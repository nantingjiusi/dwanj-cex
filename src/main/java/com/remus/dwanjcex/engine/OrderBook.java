package com.remus.dwanjcex.engine;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.dto.OrderBookLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderBook {

    // 价格精度，例如 8 位小数
    public static final int PRICE_SCALE = 8;
    public static final BigDecimal PRICE_MULTIPLIER = new BigDecimal(10).pow(PRICE_SCALE);

    private String symbol;
    private TreeMap<Long, OrderBucket> bids = new TreeMap<>(Comparator.reverseOrder());
    private TreeMap<Long, OrderBucket> asks = new TreeMap<>();
    private Map<Long, OrderEntity> orderMap = new ConcurrentHashMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public void add(OrderEntity order) {
        long priceAsLong = toLong(order.getPrice());
        TreeMap<Long, OrderBucket> book = getBook(order.getSide());
        
        OrderBucket bucket = book.computeIfAbsent(priceAsLong, OrderBucket::new);
        bucket.add(order);
        orderMap.put(order.getId(), order);
    }

    public boolean remove(Long orderId) {
        OrderEntity order = orderMap.remove(orderId);
        if (order == null) return false;

        long priceAsLong = toLong(order.getPrice());
        TreeMap<Long, OrderBucket> book = getBook(order.getSide());
        OrderBucket bucket = book.get(priceAsLong);
        if (bucket != null) {
            bucket.remove(order);
            if (bucket.isEmpty()) {
                book.remove(priceAsLong);
            }
        }
        return true;
    }

    @JsonIgnore
    public Optional<OrderBucket> getBestBidBucket() {
        return bids.isEmpty() ? Optional.empty() : Optional.of(bids.firstEntry().getValue());
    }

    @JsonIgnore
    public Optional<OrderBucket> getBestAskBucket() {
        return asks.isEmpty() ? Optional.empty() : Optional.of(asks.firstEntry().getValue());
    }

    public Map<String, List<OrderBookLevel>> getOrderBookSnapshot() {
        Map<String, List<OrderBookLevel>> snapshot = new LinkedHashMap<>();
        snapshot.put("bids", getLevels(bids));
        snapshot.put("asks", getLevels(asks));
        return snapshot;
    }

    private List<OrderBookLevel> getLevels(TreeMap<Long, OrderBucket> book) {
        return book.values().stream()
                .map(bucket -> new OrderBookLevel(toBigDecimal(bucket.getPrice()), bucket.getTotalAmount()))
                .filter(level -> level.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
    }

    // --- Helper Methods ---

    private TreeMap<Long, OrderBucket> getBook(OrderTypes.Side side) {
        return side == OrderTypes.Side.BUY ? bids : asks;
    }

    public static long toLong(BigDecimal price) {
        return price.multiply(PRICE_MULTIPLIER).longValue();
    }

    public static BigDecimal toBigDecimal(long priceAsLong) {
        return BigDecimal.valueOf(priceAsLong).divide(PRICE_MULTIPLIER, PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
