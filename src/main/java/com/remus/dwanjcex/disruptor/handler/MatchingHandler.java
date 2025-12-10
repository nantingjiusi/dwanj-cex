package com.remus.dwanjcex.disruptor.handler;

import com.lmax.disruptor.EventHandler;
import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.disruptor.event.OrderEvent;
import com.remus.dwanjcex.disruptor.event.TradeEvent;
import com.remus.dwanjcex.engine.OrderBook;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.dto.OrderBookLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件处理器 - 第二阶段：核心撮合
 * <p>
 * 这是新的撮合引擎核心。它完全在内存中运行，不涉及任何数据库IO。
 * 它消费日志处理器处理过的事件，执行订单匹配，并产生交易事件。
 * </p>
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/17 10:30
 */
@Slf4j
@Component
public class MatchingHandler implements EventHandler<OrderEvent> {

    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();

    private volatile Map<String, Map<String, List<OrderBookLevel>>> snapshotCache = new ConcurrentHashMap<>();

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        log.info("[Disruptor - Matching] 开始撮合事件: {}", event);

        OrderBook orderBook = books.computeIfAbsent(event.getSymbol(), OrderBook::new);

        OrderEntity order = OrderEntity.builder()
                .id(event.getOrderId())
                .userId(event.getUserId())
                .symbol(event.getSymbol())
                .price(event.getPrice())
                .amount(event.getAmount())
                .side(event.getSide())
                .build();

        // 核心撮合逻辑，会填充event中的tradeEvents列表
        match(order, orderBook, event);

        // 撮合逻辑执行完毕后，更新该交易对的订单簿快照
        updateSnapshot(event.getSymbol(), orderBook);
    }

    private void match(OrderEntity order, OrderBook orderBook, OrderEvent orderEvent) {
        boolean isBuy = order.getSide() == OrderTypes.Side.BUY;

        while (true) {
            Optional<Map.Entry<BigDecimal, Deque<OrderEntity>>> bestOpt =
                    isBuy ? orderBook.bestAsk() : orderBook.bestBid();

            if (bestOpt.isEmpty()) {
                break;
            }

            BigDecimal price = bestOpt.get().getKey();
            if ((isBuy && order.getPrice().compareTo(price) < 0) ||
                    (!isBuy && order.getPrice().compareTo(price) > 0)) {
                break;
            }

            Deque<OrderEntity> queue = bestOpt.get().getValue();
            OrderEntity counterOrder = queue.peekFirst();
            if (counterOrder == null) {
                log.warn("队列为空，但价格存在于订单簿中: {}", price);
                break;
            }

            BigDecimal tradedQty = order.getRemaining().min(counterOrder.getRemaining());

            // 创建并填充TradeEvent
            TradeEvent tradeEvent = TradeEvent.builder()
                    .symbol(order.getSymbol())
                    .price(price)
                    .quantity(tradedQty)
                    .buyOrderId(isBuy ? order.getId() : counterOrder.getId())
                    .sellOrderId(isBuy ? counterOrder.getId() : order.getId())
                    .buyerUserId(isBuy ? order.getUserId() : counterOrder.getUserId())
                    .sellerUserId(isBuy ? counterOrder.getUserId() : order.getUserId())
                    .build();

            // 将成交事件添加到当前处理的OrderEvent中
            orderEvent.addTradeEvent(tradeEvent);
            log.info("[Disruptor - Matching] 产生交易事件: {}", tradeEvent);

            // 更新内存中的订单状态
            order.addFilled(tradedQty);
            counterOrder.addFilled(tradedQty);

            if (counterOrder.isFullyFilled()) {
                orderBook.remove(counterOrder);
            }

            if (order.isFullyFilled()) {
                break;
            }
        }

        if (!order.isFullyFilled()) {
            orderBook.add(order);
        }
    }

    private void updateSnapshot(String symbol, OrderBook orderBook) {
        Map<String, List<OrderBookLevel>> snapshot = orderBook.getOrderBookSnapshot();
        Map<String, Map<String, List<OrderBookLevel>>> newCache = new ConcurrentHashMap<>(this.snapshotCache);
        newCache.put(symbol, snapshot);
        this.snapshotCache = newCache;
        log.trace("更新了 {} 的订单簿快照。", symbol);
    }

    public Map<String, List<OrderBookLevel>> getOrderBookSnapshot(String symbol) {
        return this.snapshotCache.getOrDefault(symbol, Collections.emptyMap());
    }
}
