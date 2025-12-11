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
        // 1. 处理有效成交
        List<TradeEvent> trades = event.getTradeEvents();
        if (trades != null && !trades.isEmpty()) {
            BigDecimal totalTradedQty = BigDecimal.ZERO;
            BigDecimal totalTradedCost = BigDecimal.ZERO;

            for (TradeEvent trade : trades) {
                SymbolEntity symbol = symbolService.getSymbol(trade.getSymbol());
                if (symbol == null) continue;

                BigDecimal tradedCost = trade.getPrice().multiply(trade.getQuantity()).setScale(symbol.getQuoteScale(), RoundingMode.DOWN);
                totalTradedQty = totalTradedQty.add(trade.getQuantity());
                totalTradedCost = totalTradedCost.add(tradedCost);

                walletService.reduceFrozen(trade.getBuyerUserId(), symbol.getQuoteCoin(), tradedCost);
                walletService.reduceFrozen(trade.getSellerUserId(), symbol.getBaseCoin(), trade.getQuantity());
                walletService.settleCredit(trade.getBuyerUserId(), symbol.getBaseCoin(), trade.getQuantity(), "trade");
                walletService.settleCredit(trade.getSellerUserId(), symbol.getQuoteCoin(), tradedCost, "trade");

                Trade tradeEntity = Trade.builder()
                        .buyOrderId(trade.getBuyOrderId()).sellOrderId(trade.getSellOrderId())
                        .symbol(trade.getSymbol()).price(trade.getPrice()).quantity(trade.getQuantity())
                        .build();
                tradeMapper.insert(tradeEntity);

                updateOrderStatus(trade.getBuyOrderId(), trade.getQuantity(), tradedCost);
                updateOrderStatus(trade.getSellOrderId(), trade.getQuantity(), tradedCost);

                eventPublisher.publishEvent(new TradeExecutedEvent(this, tradeEntity));
            }

            OrderEntity incomingOrder = orderMapper.selectById(event.getOrderId());
            if (incomingOrder != null && incomingOrder.getType() == OrderTypes.OrderType.MARKET) {
                if (totalTradedQty.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal avgPrice = totalTradedCost.divide(totalTradedQty, symbolService.getSymbol(incomingOrder.getSymbol()).getQuoteScale(), RoundingMode.HALF_UP);
                    incomingOrder.setPrice(avgPrice);
                    orderMapper.update(incomingOrder);
                }
            }
        }

        // 2. 处理新订单的最终状态
        OrderEntity finalOrderState = orderMapper.selectById(event.getOrderId());
        if (finalOrderState == null) return;

        // 2.1 如果订单因为STP策略而提前结束
        if (event.isSelfTradeCancel()) {
            String reason = "Self-trade detected (Expire Taker)";
            log.warn("订单 {} 因 '{}' 而关闭，解冻剩余资金。", finalOrderState.getId(), reason);

            if (finalOrderState.getType() == OrderTypes.OrderType.MARKET && finalOrderState.getSide() == OrderTypes.Side.BUY) {
                finalOrderState.setAmount(finalOrderState.getFilled());
            }

            unfreezeRemainingFunds(finalOrderState, reason);
            
            if (finalOrderState.getFilled().compareTo(BigDecimal.ZERO) > 0) {
                finalOrderState.setStatus(OrderStatus.PARTIALLY_FILLED_AND_CLOSED);
            } else {
                finalOrderState.setStatus(OrderStatus.CANCELED);
            }
            orderMapper.update(finalOrderState);
            return; // STP策略优先级最高，处理完直接返回
        }

        // 2.2 如果是市价单，处理其最终状态 (深度不足)
        if (finalOrderState.getType() == OrderTypes.OrderType.MARKET) {
            if (finalOrderState.getSide() == OrderTypes.Side.BUY) {
                finalOrderState.setAmount(finalOrderState.getFilled());
            }

            if (!finalOrderState.isFullyFilled()) {
                unfreezeRemainingMarketOrderFunds(finalOrderState);
                finalOrderState.setStatus(OrderStatus.PARTIALLY_FILLED_AND_CLOSED);
                eventPublisher.publishEvent(new OrderCancelNotificationEvent(this, finalOrderState.getUserId(), finalOrderState.getId(), "Market order closed due to insufficient depth"));
            }
            orderMapper.update(finalOrderState);
        }
    }

    private void handleCancelOrderPersistence(DisruptorEvent event) {
        CancelOrderDto dto = event.getCancelOrder();
        cancelExistingOrder(dto.getOrderId(), "Cancelled by user");
    }

    private void cancelExistingOrder(Long orderId, String reason) {
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null) return;
        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PARTIAL) return;
        unfreezeRemainingFunds(order, reason);
        order.setStatus(OrderStatus.CANCELED);
        orderMapper.update(order);
    }

    private void unfreezeRemainingMarketOrderFunds(OrderEntity order) {
        SymbolEntity symbol = symbolService.getSymbol(order.getSymbol());
        if (symbol == null) {
            log.error("解冻失败：找不到交易对 {}", order.getSymbol());
            return;
        }

        if (order.getSide() == OrderTypes.Side.BUY) {
            BigDecimal remainingQuote = order.getQuoteAmount().subtract(order.getQuoteFilled());
            if (remainingQuote.compareTo(BigDecimal.ZERO) > 0) {
                walletService.unfreeze(order.getUserId(), symbol.getQuoteCoin(), remainingQuote, "market_partial_unfreeze:" + order.getId());
            }
        } else { // SELL
            BigDecimal remainingQty = order.getAmount().subtract(order.getFilled());
            if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                walletService.unfreeze(order.getUserId(), symbol.getBaseCoin(), remainingQty, "market_partial_unfreeze:" + order.getId());
            }
        }
    }

    private void unfreezeRemainingFunds(OrderEntity order, String reason) {
        if (order.getStatus() == OrderStatus.CANCELED || order.getStatus() == OrderStatus.FILLED || order.getStatus() == OrderStatus.PARTIALLY_FILLED_AND_CLOSED) {
            return;
        }

        SymbolEntity symbol = symbolService.getSymbol(order.getSymbol());
        if (symbol == null) return;

        if (order.getSide() == OrderTypes.Side.BUY) {
            BigDecimal amountToUnfreeze;
            if (order.getType() == OrderTypes.OrderType.LIMIT) {
                BigDecimal remainingQty = order.getAmount().subtract(order.getFilled());
                amountToUnfreeze = order.getPrice().multiply(remainingQty).setScale(symbol.getQuoteScale(), RoundingMode.HALF_UP);
            } else { // MARKET BUY
                amountToUnfreeze = order.getQuoteAmount().subtract(order.getQuoteFilled());
            }
            if (amountToUnfreeze.compareTo(BigDecimal.ZERO) > 0) {
                walletService.unfreeze(order.getUserId(), symbol.getQuoteCoin(), amountToUnfreeze, reason + ":" + order.getId());
            }
        } else { // SELL
            BigDecimal remainingQty = order.getAmount().subtract(order.getFilled());
            if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                walletService.unfreeze(order.getUserId(), symbol.getBaseCoin(), remainingQty, reason + ":" + order.getId());
            }
        }
        eventPublisher.publishEvent(new OrderCancelNotificationEvent(this, order.getUserId(), order.getId(), reason));
    }

    private void updateOrderStatus(Long orderId, BigDecimal tradedQty, BigDecimal tradedCost) {
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null) return;

        order.addFilled(tradedQty);
        if (order.getType() == OrderTypes.OrderType.MARKET && order.getSide() == OrderTypes.Side.BUY) {
            order.addQuoteFilled(tradedCost);
        }

        if (order.isFullyFilled()) {
            order.setStatus(OrderStatus.FILLED);
        } else {
            order.setStatus(OrderStatus.PARTIAL);
        }
        orderMapper.update(order);
    }
}
