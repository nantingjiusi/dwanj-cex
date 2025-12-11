package com.remus.dwanjcex.engine.strategy;

import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.disruptor.event.DisruptorEvent;
import com.remus.dwanjcex.disruptor.event.TradeEvent;
import com.remus.dwanjcex.engine.OrderBook;
import com.remus.dwanjcex.engine.stp.STPStrategyFactory;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;

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

        if (!event.isSelfTradeCancel() && !order.isFullyFilled()) {
            orderBook.add(order);
        }
    }

    private void matchBuyOrder(OrderEntity buyOrder, OrderBook orderBook, DisruptorEvent event) {
        while (buyOrder.getRemaining().compareTo(BigDecimal.ZERO) > 0) {
            Optional<Map.Entry<BigDecimal, Deque<OrderEntity>>> bestAskOpt = orderBook.bestAsk();
            if (bestAskOpt.isEmpty() || buyOrder.getPrice().compareTo(bestAskOpt.get().getKey()) < 0) break;

            Deque<OrderEntity> askQueue = bestAskOpt.get().getValue();
            OrderEntity sellOrder = askQueue.peekFirst();
            if (sellOrder == null) {
                orderBook.removePriceLevelIfEmpty(OrderTypes.Side.SELL, bestAskOpt.get().getKey());
                continue;
            }

            if (buyOrder.getUserId().equals(sellOrder.getUserId())) {
                boolean shouldBreak = stpStrategyFactory.getActiveStrategy().handleSelfTrade(buyOrder, sellOrder, orderBook, askQueue, event);
                if (shouldBreak) break;
                continue;
            }

            processTrade(buyOrder, sellOrder, bestAskOpt.get().getKey(), orderBook, event);
        }
    }

    private void matchSellOrder(OrderEntity sellOrder, OrderBook orderBook, DisruptorEvent event) {
        while (sellOrder.getRemaining().compareTo(BigDecimal.ZERO) > 0) {
            Optional<Map.Entry<BigDecimal, Deque<OrderEntity>>> bestBidOpt = orderBook.bestBid();
            if (bestBidOpt.isEmpty() || sellOrder.getPrice().compareTo(bestBidOpt.get().getKey()) > 0) break;

            Deque<OrderEntity> bidQueue = bestBidOpt.get().getValue();
            OrderEntity buyOrder = bidQueue.peekFirst();
            if (buyOrder == null) {
                orderBook.removePriceLevelIfEmpty(OrderTypes.Side.BUY, bestBidOpt.get().getKey());
                continue;
            }

            if (sellOrder.getUserId().equals(buyOrder.getUserId())) {
                boolean shouldBreak = stpStrategyFactory.getActiveStrategy().handleSelfTrade(sellOrder, buyOrder, orderBook, bidQueue, event);
                if (shouldBreak) break;
                continue;
            }

            processTrade(buyOrder, sellOrder, bestBidOpt.get().getKey(), orderBook, event);
        }
    }

    private void processTrade(OrderEntity buyOrder, OrderEntity sellOrder, BigDecimal price, OrderBook orderBook, DisruptorEvent event) {
        BigDecimal tradedQty = buyOrder.getRemaining().min(sellOrder.getRemaining());
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
