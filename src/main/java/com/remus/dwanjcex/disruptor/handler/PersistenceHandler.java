package com.remus.dwanjcex.disruptor.handler;

import com.lmax.disruptor.EventHandler;
import com.remus.dwanjcex.common.OrderStatus;
import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.disruptor.event.DisruptorEvent;
import com.remus.dwanjcex.disruptor.event.TradeEvent;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.SymbolEntity;
import com.remus.dwanjcex.wallet.entity.Trade;
import com.remus.dwanjcex.wallet.entity.dto.CancelOrderDto;
import com.remus.dwanjcex.wallet.mapper.OrderMapper;
import com.remus.dwanjcex.wallet.mapper.TradeMapper;
import com.remus.dwanjcex.wallet.services.SymbolService;
import com.remus.dwanjcex.wallet.services.WalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 事件处理器 - 第三阶段：持久化
 * <p>
 * 消费撮合结果事件，将成交记录、订单状态更新等写入数据库。
 * </p>
 */
@Slf4j
@Component
public class PersistenceHandler implements EventHandler<DisruptorEvent> {

    private final WalletService walletService;
    private final OrderMapper orderMapper;
    private final TradeMapper tradeMapper;
    private final SymbolService symbolService;

    public PersistenceHandler(WalletService walletService, OrderMapper orderMapper, TradeMapper tradeMapper, SymbolService symbolService) {
        this.walletService = walletService;
        this.orderMapper = orderMapper;
        this.tradeMapper = tradeMapper;
        this.symbolService = symbolService;
    }

    @Override
    @Transactional
    public void onEvent(DisruptorEvent event, long sequence, boolean endOfBatch) throws Exception {
        switch (event.getType()) {
            case PLACE_ORDER:
                handlePlaceOrderPersistence(event);
                break;
            case CANCEL_ORDER:
                handleCancelOrderPersistence(event);
                break;
            default:
                log.warn("持久化处理器收到未知的事件类型: {}", event.getType());
        }
    }

    private void handlePlaceOrderPersistence(DisruptorEvent event) {
        List<TradeEvent> trades = event.getTradeEvents();
        if (trades == null || trades.isEmpty()) {
            return;
        }

        log.info("[Disruptor - Persistence] 开始持久化 {} 个成交事件...", trades.size());

        for (TradeEvent trade : trades) {
            SymbolEntity symbol = symbolService.getSymbol(trade.getSymbol());
            if (symbol == null) {
                log.error("持久化失败：找不到交易对 {}", trade.getSymbol());
                continue;
            }
            BigDecimal tradedAmount = trade.getPrice().multiply(trade.getQuantity()).setScale(symbol.getQuoteScale(), RoundingMode.HALF_UP);

            walletService.reduceFrozen(trade.getBuyerUserId(), symbol.getQuoteCoin(), tradedAmount);
            walletService.reduceFrozen(trade.getSellerUserId(), symbol.getBaseCoin(), trade.getQuantity());

            walletService.settleCredit(trade.getBuyerUserId(), symbol.getBaseCoin(), trade.getQuantity(), "trade");
            walletService.settleCredit(trade.getSellerUserId(), symbol.getQuoteCoin(), tradedAmount, "trade");

            Trade tradeEntity = Trade.builder()
                    .buyOrderId(trade.getBuyOrderId())
                    .sellOrderId(trade.getSellOrderId())
                    .symbol(trade.getSymbol())
                    .price(trade.getPrice())
                    .quantity(trade.getQuantity())
                    .build();
            tradeMapper.insert(tradeEntity);

            updateOrderStatus(trade.getBuyOrderId(), trade.getQuantity());
            updateOrderStatus(trade.getSellOrderId(), trade.getQuantity());
        }
        log.info("[Disruptor - Persistence] 成交事件持久化完成。");
    }

    private void handleCancelOrderPersistence(DisruptorEvent event) {
        CancelOrderDto dto = event.getCancelOrder();
        log.info("[Disruptor - Persistence] 开始持久化取消订单事件: {}", dto);

        OrderEntity order = orderMapper.selectById(dto.getOrderId());
        if (order == null) {
            log.error("取消订单持久化失败：找不到订单 {}", dto.getOrderId());
            return;
        }

        // 只有处于NEW或PARTIAL状态的订单才能被取消
        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PARTIAL) {
            log.warn("订单 {} 状态为 {}，无法取消。", order.getId(), order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.CANCELED);
        orderMapper.update(order);

        // 解冻剩余资金
        BigDecimal remaining = order.getRemaining();
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            SymbolEntity symbol = symbolService.getSymbol(order.getSymbol());
            if (symbol == null) {
                log.error("解冻失败：找不到交易对 {}", order.getSymbol());
                return;
            }

            boolean unfreezeOk;
            if (order.getSide() == OrderTypes.Side.BUY) {
                // 买单，解冻报价货币
                BigDecimal remainingAmount = order.getPrice().multiply(remaining).setScale(symbol.getQuoteScale(), RoundingMode.HALF_UP);
                unfreezeOk = walletService.unfreeze(order.getUserId(), symbol.getQuoteCoin(), remainingAmount, "cancel:" + order.getId());
            } else {
                // 卖单，解冻基础货币
                unfreezeOk = walletService.unfreeze(order.getUserId(), symbol.getBaseCoin(), remaining, "cancel:" + order.getId());
            }
            if (unfreezeOk) {
                log.info("成功为取消的订单 {} 解冻了剩余资金。", order.getId());
            } else {
                log.error("为取消的订单 {} 解冻资金失败！", order.getId());
            }
        }
        log.info("[Disruptor - Persistence] 取消订单事件持久化完成。");
    }

    private void updateOrderStatus(Long orderId, BigDecimal tradedQty) {
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null) {
            log.error("更新订单状态失败：找不到订单 {}", orderId);
            return;
        }

        order.addFilled(tradedQty);

        if (order.isFullyFilled()) {
            order.setStatus(OrderStatus.FILLED);
        } else {
            order.setStatus(OrderStatus.PARTIAL);
        }
        orderMapper.update(order);
    }
}
