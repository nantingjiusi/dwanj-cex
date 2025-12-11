package com.remus.dwanjcex.engine.strategy;

import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.disruptor.event.DisruptorEvent;
import com.remus.dwanjcex.disruptor.event.TradeEvent;
import com.remus.dwanjcex.engine.OrderBook;
import com.remus.dwanjcex.engine.OrderBucket;
import com.remus.dwanjcex.engine.stp.STPStrategyFactory;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor

public class LimitOrderMatchStrategy implements MatchStrategy {

    private final STPStrategyFactory stpStrategyFactory;

    @Override
    public void match(OrderEntity order, OrderBook orderBook, DisruptorEvent event) {
        if (order.getSide() == OrderTypes.Side.BUY) {
            matchBuyOrder(order, orderBook, event);
        } else {
            matchSellOrder(order, orderBook, event);
        }

        if (!order.isFullyFilled()) {
            orderBook.add(order);
        }
    }

    private void matchBuyOrder(OrderEntity buyOrder, OrderBook orderBook, DisruptorEvent event) {
        long buyPrice = OrderBook.toLong(buyOrder.getPrice());
        while (buyOrder.getRemaining().compareTo(BigDecimal.ZERO) > 0) {
            Optional<OrderBucket> bestAskBucketOpt = orderBook.getBestAskBucket();
            if (bestAskBucketOpt.isEmpty() || buyPrice < bestAskBucketOpt.get().getPrice()) {
                break;
            }

            OrderBucket askBucket = bestAskBucketOpt.get();
            OrderEntity sellOrder = askBucket.peek();
            if (sellOrder == null) {
                orderBook.getAsks().remove(askBucket.getPrice());
                continue;
            }

            if (buyOrder.getUserId().equals(sellOrder.getUserId())) {
                boolean shouldBreak = stpStrategyFactory.getActiveStrategy().handleSelfTrade(buyOrder, sellOrder, orderBook, null, event);
                if (shouldBreak) break;
                continue;
            }

            processTrade(buyOrder, sellOrder, OrderBook.toBigDecimal(askBucket.getPrice()), orderBook, event);
        }
    }

    private void matchSellOrder(OrderEntity sellOrder, OrderBook orderBook, DisruptorEvent event) {
        long sellPrice = OrderBook.toLong(sellOrder.getPrice());
        while (sellOrder.getRemaining().compareTo(BigDecimal.ZERO) > 0) {
            Optional<OrderBucket> bestBidBucketOpt = orderBook.getBestBidBucket();
            if (bestBidBucketOpt.isEmpty() || sellPrice > bestBidBucketOpt.get().getPrice()) {
                break;
            }

            OrderBucket bidBucket = bestBidBucketOpt.get();
            OrderEntity buyOrder = bidBucket.peek();
            if (buyOrder == null) {
                orderBook.getBids().remove(bidBucket.getPrice());
                continue;
            }

            if (sellOrder.getUserId().equals(buyOrder.getUserId())) {
                boolean shouldBreak = stpStrategyFactory.getActiveStrategy().handleSelfTrade(sellOrder, buyOrder, orderBook, null, event);
                if (shouldBreak) break;
                continue;
            }

            processTrade(buyOrder, sellOrder, OrderBook.toBigDecimal(bidBucket.getPrice()), orderBook, event);
        }
    }

    private void processTrade(OrderEntity buyOrder, OrderEntity sellOrder, BigDecimal price, OrderBook orderBook, DisruptorEvent event) {
        BigDecimal tradedQty = buyOrder.getRemaining().min(sellOrder.getRemaining());
        
        // 【新增】成交日志
        log.info(">>> 撮合成功: BuyOrder[{}] vs SellOrder[{}] | Price: {} | Qty: {}", 
                buyOrder.getId(), sellOrder.getId(), price, tradedQty);

        buyOrder.addFilled(tradedQty);
        sellOrder.addFilled(tradedQty);

        event.addTradeEvent(createTradeEvent(buyOrder, sellOrder, price, tradedQty));

        if (buyOrder.isFullyFilled()) {
            orderBook.remove(buyOrder.getId());
        }
        if (sellOrder.isFullyFilled()) {
            orderBook.remove(sellOrder.getId());
        }
    }

    private TradeEvent createTradeEvent(OrderEntity buyOrder, OrderEntity sellOrder, BigDecimal price, BigDecimal quantity) {
        return TradeEvent.builder()
                .symbol(buyOrder.getSymbol()).price(price).quantity(quantity)
                .buyOrderId(buyOrder.getId()).sellOrderId(sellOrder.getId())
                .buyerUserId(buyOrder.getUserId()).sellerUserId(sellOrder.getUserId())
                .build();
    }
}
