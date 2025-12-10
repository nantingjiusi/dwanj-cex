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
import com.remus.dwanjcex.websocket.event.OrderBookUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MatchingHandler implements EventHandler<DisruptorEvent> {

    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;

    private volatile Map<String, Map<String, List<OrderBookLevel>>> snapshotCache = new ConcurrentHashMap<>();

    public MatchingHandler(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void onEvent(DisruptorEvent event, long sequence, boolean endOfBatch) {
        String symbolToUpdate = null;
        switch (event.getType()) {
            case PLACE_ORDER:
                symbolToUpdate = handlePlaceOrder(event);
                break;
            case CANCEL_ORDER:
                symbolToUpdate = handleCancelOrder(event);
                break;
            default:
                log.warn("未知的事件类型: {}", event.getType());
        }

        if (symbolToUpdate != null) {
            OrderBook orderBook = books.get(symbolToUpdate);
            if (orderBook != null) {
                updateSnapshotAndPublish(symbolToUpdate, orderBook);
            }
        }
    }

    private String handlePlaceOrder(DisruptorEvent event) {
        OrderDto dto = event.getPlaceOrder();
        OrderBook orderBook = books.computeIfAbsent(dto.getSymbol(), OrderBook::new);
        OrderEntity order = OrderEntity.builder()
                .id(event.getOrderId())
                .userId(dto.getUserId())
                .symbol(dto.getSymbol())
                .price(dto.getPrice())
                .amount(dto.getAmount())
                .side(dto.getSide())
                .build();
        match(order, orderBook, event);
        return dto.getSymbol();
    }

    private String handleCancelOrder(DisruptorEvent event) {
        CancelOrderDto dto = event.getCancelOrder();
        OrderBook orderBook = books.get(dto.getSymbol());
        if (orderBook == null) return null;
        if (orderBook.remove(dto.getOrderId())) {
            return dto.getSymbol();
        }
        return null;
    }

    private void match(OrderEntity order, OrderBook orderBook, DisruptorEvent event) {
        if (order.getSide() == OrderTypes.Side.BUY) {
            matchBuyOrder(order, orderBook, event);
        } else {
            matchSellOrder(order, orderBook, event);
        }

        if (!event.isSelfTradeCancel() && !order.isFullyFilled()) {
            orderBook.add(order);
        }
    }

    private void matchBuyOrder(OrderEntity buyOrder, OrderBook orderBook, DisruptorEvent event) {
        while (buyOrder.getRemaining().compareTo(BigDecimal.ZERO) > 0) {
            Optional<Map.Entry<BigDecimal, Deque<OrderEntity>>> bestAskOpt = orderBook.bestAsk();
            if (bestAskOpt.isEmpty()) break;

            BigDecimal bestAskPrice = bestAskOpt.get().getKey();
            if (buyOrder.getPrice().compareTo(bestAskPrice) < 0) break;

            Deque<OrderEntity> askQueue = bestAskOpt.get().getValue();
            OrderEntity sellOrder = askQueue.peekFirst();

            if (sellOrder == null) {
                orderBook.removePriceLevelIfEmpty(OrderTypes.Side.SELL, bestAskPrice);
                continue;
            }

            if (buyOrder.getUserId().equals(sellOrder.getUserId())) {
                log.warn("检测到自成交! 新买单 {} 将被取消。", buyOrder.getId());
                event.setSelfTradeCancel(true);
                return;
            }

            BigDecimal tradedQty = buyOrder.getRemaining().min(sellOrder.getRemaining());
            TradeEvent tradeEvent = createTradeEvent(buyOrder, sellOrder, bestAskPrice, tradedQty);
            event.addTradeEvent(tradeEvent);

            buyOrder.addFilled(tradedQty);
            sellOrder.addFilled(tradedQty);

            if (sellOrder.isFullyFilled()) {
                askQueue.pollFirst();
                orderBook.remove(sellOrder.getId());
            }
            orderBook.removePriceLevelIfEmpty(OrderTypes.Side.SELL, bestAskPrice);
        }
    }

    private void matchSellOrder(OrderEntity sellOrder, OrderBook orderBook, DisruptorEvent event) {
        while (sellOrder.getRemaining().compareTo(BigDecimal.ZERO) > 0) {
            Optional<Map.Entry<BigDecimal, Deque<OrderEntity>>> bestBidOpt = orderBook.bestBid();
            if (bestBidOpt.isEmpty()) break;

            BigDecimal bestBidPrice = bestBidOpt.get().getKey();
            if (sellOrder.getPrice().compareTo(bestBidPrice) > 0) break;

            Deque<OrderEntity> bidQueue = bestBidOpt.get().getValue();
            OrderEntity buyOrder = bidQueue.peekFirst();

            if (buyOrder == null) {
                orderBook.removePriceLevelIfEmpty(OrderTypes.Side.BUY, bestBidPrice);
                continue;
            }

            if (sellOrder.getUserId().equals(buyOrder.getUserId())) {
                log.warn("检测到自成交! 新卖单 {} 将被取消。", sellOrder.getId());
                event.setSelfTradeCancel(true);
                return;
            }

            BigDecimal tradedQty = sellOrder.getRemaining().min(buyOrder.getRemaining());
            TradeEvent tradeEvent = createTradeEvent(buyOrder, sellOrder, bestBidPrice, tradedQty);
            event.addTradeEvent(tradeEvent);

            sellOrder.addFilled(tradedQty);
            buyOrder.addFilled(tradedQty);

            if (buyOrder.isFullyFilled()) {
                bidQueue.pollFirst();
                orderBook.remove(buyOrder.getId());
            }
            orderBook.removePriceLevelIfEmpty(OrderTypes.Side.BUY, bestBidPrice);
        }
    }

    private TradeEvent createTradeEvent(OrderEntity buyOrder, OrderEntity sellOrder, BigDecimal price, BigDecimal quantity) {
        return TradeEvent.builder()
                .symbol(buyOrder.getSymbol())
                .price(price)
                .quantity(quantity)
                .buyOrderId(buyOrder.getId())
                .sellOrderId(sellOrder.getId())
                .buyerUserId(buyOrder.getUserId())
                .sellerUserId(sellOrder.getUserId())
                .build();
    }

    private void updateSnapshotAndPublish(String symbol, OrderBook orderBook) {
        Map<String, List<OrderBookLevel>> snapshot = orderBook.getOrderBookSnapshot();
        Map<String, Map<String, List<OrderBookLevel>>> newCache = new ConcurrentHashMap<>(this.snapshotCache);
        newCache.put(symbol, snapshot);
        this.snapshotCache = newCache;
        eventPublisher.publishEvent(new OrderBookUpdateEvent(this, symbol, snapshot));
    }

    public Map<String, List<OrderBookLevel>> getOrderBookSnapshot(String symbol) {
        return this.snapshotCache.getOrDefault(symbol, Collections.emptyMap());
    }
}
