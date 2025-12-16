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
import java.math.RoundingMode;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketOrderMatchStrategy implements MatchStrategy {

    private final STPStrategyFactory stpStrategyFactory;

    @Override
    public void match(OrderEntity order, OrderBook orderBook, DisruptorEvent event) {
        if (order.getSide() == OrderTypes.Side.BUY) {
            matchMarketBuy(order, orderBook, event);
        } else {
            matchMarketSell(order, orderBook, event);
        }
    }

    private void matchMarketBuy(OrderEntity buyOrder, OrderBook orderBook, DisruptorEvent event) {
        BigDecimal amountToSpend = buyOrder.getQuoteAmount();

        while (buyOrder.getQuoteFilled().compareTo(amountToSpend) < 0) {
            Optional<OrderBucket> bestAskBucketOpt = orderBook.getBestAskBucket();
            if (bestAskBucketOpt.isEmpty()) break;

            OrderBucket askBucket = bestAskBucketOpt.get();
            BigDecimal price = OrderBook.toBigDecimal(askBucket.getPrice());
            OrderEntity sellOrder = askBucket.peek();
            if (sellOrder == null) {
                orderBook.getAsks().remove(askBucket.getPrice());
                continue;
            }

            if (buyOrder.getUserId().equals(sellOrder.getUserId())) {
                boolean shouldBreak = stpStrategyFactory.getActiveStrategy().handleSelfTrade(buyOrder, sellOrder, orderBook, null, event);
                if (shouldBreak) {
                    break;
                }
                continue;
            }

            BigDecimal remainingSpend = amountToSpend.subtract(buyOrder.getQuoteFilled());
            BigDecimal maxQtyToBuy = remainingSpend.divide(price, 8, RoundingMode.DOWN);
            BigDecimal tradedQty = maxQtyToBuy.min(sellOrder.getRemaining());

            if (tradedQty.compareTo(BigDecimal.ZERO) <= 0) break;

            processTrade(buyOrder, sellOrder, price, tradedQty, orderBook, event);
            
            if (askBucket.isEmpty()) orderBook.getAsks().remove(askBucket.getPrice());
        }
    }

    private void matchMarketSell(OrderEntity sellOrder, OrderBook orderBook, DisruptorEvent event) {
        while (sellOrder.getRemaining().compareTo(BigDecimal.ZERO) > 0) {
            Optional<OrderBucket> bestBidBucketOpt = orderBook.getBestBidBucket();
            if (bestBidBucketOpt.isEmpty()) break;

            OrderBucket bidBucket = bestBidBucketOpt.get();
            BigDecimal price = OrderBook.toBigDecimal(bidBucket.getPrice());
            OrderEntity buyOrder = bidBucket.peek();
            if (buyOrder == null) {
                orderBook.getBids().remove(bidBucket.getPrice());
                continue;
            }

            if (sellOrder.getUserId().equals(buyOrder.getUserId())) {
                boolean shouldBreak = stpStrategyFactory.getActiveStrategy().handleSelfTrade(sellOrder, buyOrder, orderBook, null, event);
                if (shouldBreak) {
                    break;
                }
                continue;
            }

            BigDecimal tradedQty = sellOrder.getRemaining().min(buyOrder.getRemaining());

            processTrade(sellOrder, buyOrder, price, tradedQty, orderBook, event);

            if (bidBucket.isEmpty()) orderBook.getBids().remove(bidBucket.getPrice());
        }
    }

    private void processTrade(OrderEntity takerOrder, OrderEntity makerOrder, BigDecimal price, BigDecimal tradedQty, OrderBook orderBook, DisruptorEvent event) {
        log.info(">>> 撮合成功: Taker[{}] vs Maker[{}] | Price: {} | Qty: {}", 
                takerOrder.getId(), makerOrder.getId(), price, tradedQty);

        BigDecimal cost = tradedQty.multiply(price);
        
        takerOrder.addFilled(tradedQty);
        if (takerOrder.getType() == OrderTypes.OrderType.MARKET && takerOrder.getSide() == OrderTypes.Side.BUY) {
            takerOrder.addQuoteFilled(cost);
        }
        
        makerOrder.addFilled(tradedQty);
        if (makerOrder.getType() == OrderTypes.OrderType.MARKET && makerOrder.getSide() == OrderTypes.Side.BUY) {
            makerOrder.addQuoteFilled(cost);
        }

        event.addTradeEvent(createTradeEvent(takerOrder, makerOrder, price, tradedQty));

        if (takerOrder.isFullyFilled()) {
            orderBook.remove(takerOrder.getId());
        }
        if (makerOrder.isFullyFilled()) {
            orderBook.remove(makerOrder.getId());
        }
    }

    private TradeEvent createTradeEvent(OrderEntity takerOrder, OrderEntity makerOrder, BigDecimal price, BigDecimal quantity) {
        return TradeEvent.builder()
                .symbol(takerOrder.getMarketSymbol())
                .price(price)
                .quantity(quantity)
                .takerOrderId(takerOrder.getId())
                .makerOrderId(makerOrder.getId())
                .takerUserId(takerOrder.getUserId())
                .makerUserId(makerOrder.getUserId())
                .build();
    }
}
