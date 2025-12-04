package com.remus.dwanjcex.engine;

import com.remus.dwanjcex.common.OrderStatus;
import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.common.SymbolEnum;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.Trade;
import com.remus.dwanjcex.wallet.mapper.OrderMapper;
import com.remus.dwanjcex.wallet.mapper.TradeMapper;
import com.remus.dwanjcex.wallet.services.WalletService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Component
public class MatchingEngine {

    private final Map<SymbolEnum, OrderBook> books = new HashMap<>();
    private final OrderMapper orderMapper;
    private final TradeMapper tradeMapper;
    private final WalletService walletService;
    private static final int SCALE = 8;

    public MatchingEngine(OrderMapper orderMapper, TradeMapper tradeMapper, WalletService walletService) {
        this.orderMapper = orderMapper;
        this.tradeMapper = tradeMapper;
        this.walletService = walletService;
    }

    private OrderBook getBook(SymbolEnum symbol) {
        return books.computeIfAbsent(symbol, k -> new OrderBook(symbol));
    }

    @Transactional
    public synchronized void place(OrderEntity order) {
        order.setStatus(OrderStatus.NEW);
        orderMapper.update(order);
        matchOrder(order);
        if (!order.isFullyFilled()) {
            order.setStatus(OrderStatus.PARTIAL);
            getBook(order.getSymbol()).add(order);
        } else {
            order.setStatus(OrderStatus.FILLED);
        }
        orderMapper.update(order);
    }

    private void matchOrder(OrderEntity order) {
        boolean isBuy = order.getSide() == OrderTypes.Side.BUY;
        OrderBook orderBook = getBook(order.getSymbol());

        while (true) {
            Optional<Map.Entry<BigDecimal, Deque<OrderEntity>>> bestOpt =
                    isBuy ? orderBook.bestAsk() : orderBook.bestBid();
            if (bestOpt.isEmpty()) break;

            BigDecimal price = bestOpt.get().getKey();
            if ((isBuy && order.getPrice().compareTo(price) < 0) ||
                    (!isBuy && order.getPrice().compareTo(price) > 0)) break;

            Deque<OrderEntity> queue = bestOpt.get().getValue();
            OrderEntity counterOrder = queue.peekFirst();
            if (counterOrder == null) break;

            BigDecimal traded = order.getRemaining()
                    .min(counterOrder.getRemaining())
                    .setScale(SCALE, BigDecimal.ROUND_HALF_UP);

            // ----------------- 处理冻结资金 -----------------
            // 卖方减少冻结基础币
            walletService.reduceFrozen(counterOrder.getUserId(), counterOrder.getSymbol().getBaseCoin(), traded);
            // 买方减少冻结报价币
            walletService.reduceFrozen(order.getUserId(), order.getSymbol().getQuoteCoin(), traded.multiply(price).setScale(SCALE, BigDecimal.ROUND_HALF_UP));

            // ----------------- 结算资产 -----------------
            // 买方增加基础币可用余额
            walletService.settleCredit(order.getUserId(), order.getSymbol().getBaseCoin(), traded, "trade");
            // 卖方增加报价币可用余额
            walletService.settleCredit(counterOrder.getUserId(), counterOrder.getSymbol().getQuoteCoin(), traded.multiply(price).setScale(SCALE, BigDecimal.ROUND_HALF_UP), "trade");

            // ----------------- 插入成交记录 -----------------
            tradeMapper.insert(Trade.builder()
                    .buyOrderId(isBuy ? order.getId() : counterOrder.getId())
                    .sellOrderId(isBuy ? counterOrder.getId() : order.getId())
                    .symbol(order.getSymbol().getSymbol())
                    .price(price)
                    .quantity(traded)
                    .build());

            // 更新已成交数量
            order.addFilled(traded);
            counterOrder.addFilled(traded);

            orderMapper.update(order);
            orderMapper.update(counterOrder);

            // 移除完全成交订单
            if (counterOrder.isFullyFilled()) {
                orderBook.remove(counterOrder);
                counterOrder.setStatus(OrderStatus.FILLED);
                orderMapper.update(counterOrder);
            }

            if (order.isFullyFilled()) {
                order.setStatus(OrderStatus.FILLED);
                break;
            }
        }
    }


    @Transactional
    public synchronized void cancel(OrderEntity order) {
        getBook(order.getSymbol()).remove(order);
        order.setStatus(OrderStatus.CANCELED);
        orderMapper.update(order);

        BigDecimal remaining = order.getRemaining();
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            walletService.unfreeze(order.getUserId(), order.getSymbol().getBaseCoin(), remaining, "cancel");
        }
    }

    /** 获取指定交易对订单簿快照 */
    public Map<String, Map<BigDecimal, List<OrderEntity>>> getOrderBook(SymbolEnum symbol){
        return getBook(symbol).getOrderBookSnapshot();
    }
}
