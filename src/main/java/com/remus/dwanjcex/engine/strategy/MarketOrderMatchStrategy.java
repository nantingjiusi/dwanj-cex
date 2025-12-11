package com.remus.dwanjcex.engine.strategy;

import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.disruptor.event.DisruptorEvent;
import com.remus.dwanjcex.disruptor.event.TradeEvent;
import com.remus.dwanjcex.engine.OrderBook;
import com.remus.dwanjcex.engine.stp.STPStrategyFactory;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Deque;
import java.util.Map;
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
            Optional<Map.Entry<BigDecimal, Deque<OrderEntity>>> bestAskOpt = orderBook.bestAsk();
            if (bestAskOpt.isEmpty()) {
                log.warn("市价买单 {} 因深度不足而提前终止。", buyOrder.getId());
                break;
            }

            BigDecimal price = bestAskOpt.get().getKey();
            Deque<OrderEntity> askQueue = bestAskOpt.get().getValue();
            OrderEntity sellOrder = askQueue.peekFirst();
            if (sellOrder == null) {
                orderBook.removePriceLevelIfEmpty(OrderTypes.Side.SELL, price);
                continue;
            }

            if (buyOrder.getUserId().equals(sellOrder.getUserId())) {
                boolean shouldBreak = stpStrategyFactory.getActiveStrategy().handleSelfTrade(buyOrder, sellOrder, orderBook, askQueue, event);
                if (shouldBreak) break;
                continue;
            }

            BigDecimal remainingSpend = amountToSpend.subtract(buyOrder.getQuoteFilled());
            BigDecimal maxQtyToBuy = remainingSpend.divide(price, 8, RoundingMode.DOWN);
            BigDecimal tradedQty = maxQtyToBuy.min(sellOrder.getRemaining());

            if (tradedQty.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal cost = tradedQty.multiply(price);
            
            buyOrder.addFilled(tradedQty);
            buyOrder.addQuoteFilled(cost);
            sellOrder.addFilled(tradedQty);

            event.addTradeEvent(createTradeEvent(buyOrder, sellOrder, price, tradedQty));

            if (sellOrder.isFullyFilled()) {
                askQueue.pollFirst();
                orderBook.remove(sellOrder.getId());
            }
            orderBook.removePriceLevelIfEmpty(OrderTypes.Side.SELL, price);
        }
    }

    private void matchMarketSell(OrderEntity sellOrder, OrderBook orderBook, DisruptorEvent event) {
        while (sellOrder.getRemaining().compareTo(BigDecimal.ZERO) > 0) {
            Optional<Map.Entry<BigDecimal, Deque<OrderEntity>>> bestBidOpt = orderBook.bestBid();
            if (bestBidOpt.isEmpty()) {
                log.warn("市价卖单 {} 因深度不足而提前终止。", sellOrder.getId());
                break;
            }

            BigDecimal price = bestBidOpt.get().getKey();
            Deque<OrderEntity> bidQueue = bestBidOpt.get().getValue();
            OrderEntity buyOrder = bidQueue.peekFirst();
            if (buyOrder == null) {
                orderBook.removePriceLevelIfEmpty(OrderTypes.Side.BUY, price);
                continue;
            }

            if (sellOrder.getUserId().equals(buyOrder.getUserId())) {
                boolean shouldBreak = stpStrategyFactory.getActiveStrategy().handleSelfTrade(sellOrder, buyOrder, orderBook, bidQueue, event);
                if (shouldBreak) break;
                continue;
            }

            BigDecimal tradedQty = sellOrder.getRemaining().min(buyOrder.getRemaining());

            sellOrder.addFilled(tradedQty);
            buyOrder.addFilled(tradedQty);
            buyOrder.addQuoteFilled(tradedQty.multiply(price));

            event.addTradeEvent(createTradeEvent(buyOrder, sellOrder, price, tradedQty));

            if (buyOrder.isFullyFilled()) {
                bidQueue.pollFirst();
                orderBook.remove(buyOrder.getId());
            }
            orderBook.removePriceLevelIfEmpty(OrderTypes.Side.BUY, price);
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
