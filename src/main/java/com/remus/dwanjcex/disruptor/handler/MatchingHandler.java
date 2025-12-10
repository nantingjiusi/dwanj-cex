package com.remus.dwanjcex.disruptor.handler;

import com.lmax.disruptor.EventHandler;
import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.disruptor.event.DisruptorEvent;
import com.remus.dwanjcex.disruptor.event.TradeEvent;
import com.remus.dwanjcex.engine.OrderBook;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.dto.CancelOrderDto;
import com.remus.dwanjcex.wallet.entity.dto.OrderBookLevel;
import com.remus.dwanjcex.wallet.entity.dto.OrderDto;
import com.remus.dwanjcex.wallet.services.SymbolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 事件处理器 - 核心撮合
 * <p>
 * 消费Disruptor事件，执行订单匹配或取消，并产生交易事件。
 * </p>
 */
@Slf4j
@Component
public class MatchingHandler implements EventHandler<DisruptorEvent> {

    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();
    private final SymbolService symbolService;

    private volatile Map<String, Map<String, List<OrderBookLevel>>> snapshotCache = new ConcurrentHashMap<>();

    public MatchingHandler(SymbolService symbolService) {
        this.symbolService = symbolService;
    }

    @Override
    public void onEvent(DisruptorEvent event, long sequence, boolean endOfBatch) throws Exception {
        switch (event.getType()) {
            case PLACE_ORDER:
                handlePlaceOrder(event);
                break;
            case CANCEL_ORDER:
                handleCancelOrder(event);
                break;
            default:
                log.warn("未知的事件类型: {}", event.getType());
        }
    }

    private void handlePlaceOrder(DisruptorEvent event) {
        OrderDto dto = event.getPlaceOrder();
        log.info("[Disruptor - Matching] 开始处理下单事件: {}", dto);

        OrderBook orderBook = books.computeIfAbsent(dto.getSymbol(), OrderBook::new);

        OrderEntity order = OrderEntity.builder()
                .id(event.getOrderId()) // 从DisruptorEvent中获取orderId
                .userId(dto.getUserId())
                .symbol(dto.getSymbol())
                .price(dto.getPrice())
                .amount(dto.getAmount())
                .side(dto.getSide())
                .build();

        match(order, orderBook, event);
        updateSnapshot(dto.getSymbol(), orderBook);
    }

    private void handleCancelOrder(DisruptorEvent event) {
        CancelOrderDto dto = event.getCancelOrder();
        log.info("[Disruptor - Matching] 开始处理取消订单事件: {}", dto);

        OrderBook orderBook = books.get(dto.getSymbol());
        if (orderBook == null) {
            log.warn("尝试取消一个不存在的订单簿中的订单: {}", dto);
            return;
        }

        boolean removed = orderBook.remove(dto.getOrderId(), dto.getSide());
        if (removed) {
            log.info("成功从内存订单簿中移除订单: {}", dto.getOrderId());
            updateSnapshot(dto.getSymbol(), orderBook);
        } else {
            log.warn("尝试从订单簿中移除一个不存在的订单: {}", dto.getOrderId());
        }
    }

    private void match(OrderEntity order, OrderBook orderBook, DisruptorEvent event) {
        boolean isBuy = order.getSide() == OrderTypes.Side.BUY;

        while (true) {
            Optional<Map.Entry<BigDecimal, Deque<OrderEntity>>> bestOpt =
                    isBuy ? orderBook.bestAsk() : orderBook.bestBid();

            if (bestOpt.isEmpty()) break;

            BigDecimal price = bestOpt.get().getKey();
            if ((isBuy && order.getPrice().compareTo(price) < 0) ||
                    (!isBuy && order.getPrice().compareTo(price) > 0)) break;

            Deque<OrderEntity> queue = bestOpt.get().getValue();
            OrderEntity counterOrder = queue.peekFirst();
            if (counterOrder == null) {
                log.warn("队列为空，但价格存在于订单簿中: {}", price);
                break;
            }

            BigDecimal tradedQty = order.getRemaining().min(counterOrder.getRemaining());

            TradeEvent tradeEvent = TradeEvent.builder()
                    .symbol(order.getSymbol())
                    .price(price)
                    .quantity(tradedQty)
                    .buyOrderId(isBuy ? order.getId() : counterOrder.getId())
                    .sellOrderId(isBuy ? counterOrder.getId() : order.getId())
                    .buyerUserId(isBuy ? order.getUserId() : counterOrder.getUserId())
                    .sellerUserId(isBuy ? counterOrder.getUserId() : order.getUserId())
                    .build();

            event.addTradeEvent(tradeEvent);
            log.info("[Disruptor - Matching] 产生交易事件: {}", tradeEvent);

            order.addFilled(tradedQty);
            counterOrder.addFilled(tradedQty);

            if (counterOrder.isFullyFilled()) {
                orderBook.remove(counterOrder.getId(), counterOrder.getSide());
            }

            if (order.isFullyFilled()) break;
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
