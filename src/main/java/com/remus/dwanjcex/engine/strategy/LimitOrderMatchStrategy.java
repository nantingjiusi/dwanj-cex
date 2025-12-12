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
        // 【关键修复】将撮合逻辑的返回值作为是否挂单的依据
        boolean shouldAddToBook = true; 
        if (order.getSide() == OrderTypes.Side.BUY) {
            shouldAddToBook = matchBuyOrder(order, orderBook, event);
        } else {
            shouldAddToBook = matchSellOrder(order, orderBook, event);
        }

        // 只有当撮合逻辑允许，并且订单未完全成交时，才将其加入订单簿
        if (shouldAddToBook && !order.isFullyFilled()) {
            orderBook.add(order);
        }
    }

    private boolean matchBuyOrder(OrderEntity buyOrder, OrderBook orderBook, DisruptorEvent event) {
        long buyPrice = OrderBook.toLong(buyOrder.getPrice());
        while (buyOrder.getRemaining().compareTo(BigDecimal.ZERO) > 0) {
            Optional<OrderBucket> bestAskBucketOpt = orderBook.getBestAskBucket();
            if (bestAskBucketOpt.isEmpty() || buyPrice < bestAskBucketOpt.get().getPrice()) {
                break; // 对手盘为空或价格不匹配，中断撮合，允许挂单
            }

            OrderBucket askBucket = bestAskBucketOpt.get();
            OrderEntity sellOrder = askBucket.peek();
            if (sellOrder == null) {
                orderBook.getAsks().remove(askBucket.getPrice());
                continue;
            }

            if (buyOrder.getUserId().equals(sellOrder.getUserId())) {
                boolean shouldBreak = stpStrategyFactory.getActiveStrategy().handleSelfTrade(buyOrder, sellOrder, orderBook, null, event);
                if (shouldBreak) {
                    // 如果STP策略要求中断（例如ExpireTaker），则不再将此Taker单挂入订单簿
                    return false; 
                }
                continue;
            }

            processTrade(buyOrder, sellOrder, OrderBook.toBigDecimal(askBucket.getPrice()), orderBook, event);
        }
        // 循环正常结束，意味着该订单可以被挂入订单簿
        return true;
    }

    private boolean matchSellOrder(OrderEntity sellOrder, OrderBook orderBook, DisruptorEvent event) {
        long sellPrice = OrderBook.toLong(sellOrder.getPrice());
        while (sellOrder.getRemaining().compareTo(BigDecimal.ZERO) > 0) {
            Optional<OrderBucket> bestBidBucketOpt = orderBook.getBestBidBucket();
            if (bestBidBucketOpt.isEmpty() || sellPrice > bestBidBucketOpt.get().getPrice()) {
                break; // 对手盘为空或价格不匹配，中断撮合，允许挂单
            }

            OrderBucket bidBucket = bestBidBucketOpt.get();
            OrderEntity buyOrder = bidBucket.peek();
            if (buyOrder == null) {
                orderBook.getBids().remove(bidBucket.getPrice());
                continue;
            }

            if (sellOrder.getUserId().equals(buyOrder.getUserId())) {
                boolean shouldBreak = stpStrategyFactory.getActiveStrategy().handleSelfTrade(sellOrder, buyOrder, orderBook, null, event);
                if (shouldBreak) {
                    // 如果STP策略要求中断，则不再将此Taker单挂入订单簿
                    return false;
                }
                continue;
            }

            processTrade(buyOrder, sellOrder, OrderBook.toBigDecimal(bidBucket.getPrice()), orderBook, event);
        }
        // 循环正常结束，意味着该订单可以被挂入订单簿
        return true;
    }

    private void processTrade(OrderEntity buyOrder, OrderEntity sellOrder, BigDecimal price, OrderBook orderBook, DisruptorEvent event) {
        BigDecimal tradedQty = buyOrder.getRemaining().min(sellOrder.getRemaining());
        
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
