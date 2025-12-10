package com.remus.dwanjcex.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.disruptor.event.DisruptorEvent;
import com.remus.dwanjcex.disruptor.event.TradeEvent;
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

    public void match(OrderEntity order, DisruptorEvent event) {
        if (order.getSide() == OrderTypes.Side.BUY) {
            matchBuyOrder(order, event);
        } else {
            matchSellOrder(order, event);
        }
        if (!event.isSelfTradeCancel() && !order.isFullyFilled()) {
            add(order);
        }
    }

    private void matchBuyOrder(OrderEntity buyOrder, DisruptorEvent event) {
        while (buyOrder.getRemaining().compareTo(BigDecimal.ZERO) > 0) {
            Optional<Map.Entry<BigDecimal, Deque<OrderEntity>>> bestAskOpt = bestAsk();
            if (bestAskOpt.isEmpty() || buyOrder.getPrice().compareTo(bestAskOpt.get().getKey()) < 0) break;

            Deque<OrderEntity> askQueue = bestAskOpt.get().getValue();
            OrderEntity sellOrder = askQueue.peekFirst();
            if (sellOrder == null) {
                removePriceLevelIfEmpty(OrderTypes.Side.SELL, bestAskOpt.get().getKey());
                continue;
            }
            if (buyOrder.getUserId().equals(sellOrder.getUserId())) {
                event.setSelfTradeCancel(true);
                return;
            }
            processTrade(buyOrder, sellOrder, bestAskOpt.get().getKey(), event);
        }
    }

    private void matchSellOrder(OrderEntity sellOrder, DisruptorEvent event) {
        while (sellOrder.getRemaining().compareTo(BigDecimal.ZERO) > 0) {
            Optional<Map.Entry<BigDecimal, Deque<OrderEntity>>> bestBidOpt = bestBid();
            if (bestBidOpt.isEmpty() || sellOrder.getPrice().compareTo(bestBidOpt.get().getKey()) > 0) break;

            Deque<OrderEntity> bidQueue = bestBidOpt.get().getValue();
            OrderEntity buyOrder = bidQueue.peekFirst();
            if (buyOrder == null) {
                removePriceLevelIfEmpty(OrderTypes.Side.BUY, bestBidOpt.get().getKey());
                continue;
            }
            if (sellOrder.getUserId().equals(buyOrder.getUserId())) {
                event.setSelfTradeCancel(true);
                return;
            }
            processTrade(buyOrder, sellOrder, bestBidOpt.get().getKey(), event);
        }
    }

    private void processTrade(OrderEntity buyOrder, OrderEntity sellOrder, BigDecimal price, DisruptorEvent event) {
        BigDecimal tradedQty = buyOrder.getRemaining().min(sellOrder.getRemaining());
        buyOrder.addFilled(tradedQty);
        sellOrder.addFilled(tradedQty);

        event.addTradeEvent(TradeEvent.builder()
                .symbol(symbol).price(price).quantity(tradedQty)
                .buyOrderId(buyOrder.getId()).sellOrderId(sellOrder.getId())
                .buyerUserId(buyOrder.getUserId()).sellerUserId(sellOrder.getUserId())
                .build());

        if (buyOrder.isFullyFilled()) remove(buyOrder.getId());
        if (sellOrder.isFullyFilled()) remove(sellOrder.getId());
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
