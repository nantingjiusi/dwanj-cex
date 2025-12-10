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
import com.remus.dwanjcex.websocket.event.OrderCancelNotificationEvent;
import com.remus.dwanjcex.websocket.event.TradeExecutedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Component
public class PersistenceHandler implements EventHandler<DisruptorEvent> {

    private final WalletService walletService;
    private final OrderMapper orderMapper;
    private final TradeMapper tradeMapper;
    private final SymbolService symbolService;
    private final ApplicationEventPublisher eventPublisher;

    public PersistenceHandler(WalletService walletService, OrderMapper orderMapper, TradeMapper tradeMapper, SymbolService symbolService, ApplicationEventPublisher eventPublisher) {
        this.walletService = walletService;
        this.orderMapper = orderMapper;
        this.tradeMapper = tradeMapper;
        this.symbolService = symbolService;
        this.eventPublisher = eventPublisher;
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
        if (event.isSelfTradeCancel()) {
            log.warn("[Disruptor - Persistence] 检测到自成交取消标志，开始取消新订单: {}", event.getOrderId());
            cancelNewOrder(event.getOrderId());
            return;
        }

        List<TradeEvent> trades = event.getTradeEvents();
        if (trades == null || trades.isEmpty()) {
            return;
        }

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

            // 发布交易执行事件
            eventPublisher.publishEvent(new TradeExecutedEvent(this, tradeEntity));
        }
    }

    private void handleCancelOrderPersistence(DisruptorEvent event) {
        CancelOrderDto dto = event.getCancelOrder();
        cancelExistingOrder(dto.getOrderId());
    }

    private void cancelNewOrder(Long orderId) {
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null) return;
        updateAndUnfreeze(order, "Self-trade detected");
    }

    private void cancelExistingOrder(Long orderId) {
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null) return;
        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PARTIAL) return;
        updateAndUnfreeze(order, "Cancelled by user");
    }

    private void updateAndUnfreeze(OrderEntity order, String reason) {
        order.setStatus(OrderStatus.CANCELED);
        orderMapper.update(order);

        BigDecimal remaining = order.getRemaining();
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            SymbolEntity symbol = symbolService.getSymbol(order.getSymbol());
            if (symbol == null) return;

            if (order.getSide() == OrderTypes.Side.BUY) {
                BigDecimal remainingAmount = order.getPrice().multiply(remaining).setScale(symbol.getQuoteScale(), RoundingMode.HALF_UP);
                walletService.unfreeze(order.getUserId(), symbol.getQuoteCoin(), remainingAmount, "cancel:" + order.getId());
            } else {
                walletService.unfreeze(order.getUserId(), symbol.getBaseCoin(), remaining, "cancel:" + order.getId());
            }
        }
        eventPublisher.publishEvent(new OrderCancelNotificationEvent(this, order.getUserId(), order.getId(), reason));
    }

    private void updateOrderStatus(Long orderId, BigDecimal tradedQty) {
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null) return;
        order.addFilled(tradedQty);
        if (order.isFullyFilled()) {
            order.setStatus(OrderStatus.FILLED);
        } else {
            order.setStatus(OrderStatus.PARTIAL);
        }
        orderMapper.update(order);
    }
}
