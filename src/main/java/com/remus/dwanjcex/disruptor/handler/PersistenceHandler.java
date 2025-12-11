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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PersistenceHandler implements EventHandler<DisruptorEvent> {

    private final WalletService walletService;
    private final OrderMapper orderMapper;
    private final TradeMapper tradeMapper;
    private final SymbolService symbolService;
    private final ApplicationEventPublisher eventPublisher;

    private final List<Trade> pendingTrades = new ArrayList<>();
    private final Map<Long, OrderEntity> pendingOrderUpdates = new HashMap<>();
    private static final int BATCH_SIZE = 100;

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
        try {
            switch (event.getType()) {
                case PLACE_ORDER:
                    handlePlaceOrderPersistence(event);
                    break;
                case CANCEL_ORDER:
                    handleCancelOrderPersistence(event);
                    break;
            }

            if (endOfBatch || pendingTrades.size() >= BATCH_SIZE || pendingOrderUpdates.size() >= BATCH_SIZE) {
                flush();
            }
        } catch (Exception e) {
            log.error("PersistenceHandler 处理事件失败: event={}", event, e);
        }
    }

    private void handlePlaceOrderPersistence(DisruptorEvent event) {
        if (event.getCancelledOrderIds() != null && !event.getCancelledOrderIds().isEmpty()) {
            for (Long cancelledOrderId : event.getCancelledOrderIds()) {
                try {
                    cancelExistingOrder(cancelledOrderId, "Cancelled by market order self-trade");
                } catch (Exception e) {
                    log.error("处理自成交取消挂单失败: orderId={}", cancelledOrderId, e);
                }
            }
        }

        List<TradeEvent> trades = event.getTradeEvents();
        if (trades != null && !trades.isEmpty()) {
            BigDecimal totalTradedQty = BigDecimal.ZERO;
            BigDecimal totalTradedCost = BigDecimal.ZERO;

            for (TradeEvent trade : trades) {
                try {
                    SymbolEntity symbol = symbolService.getSymbol(trade.getSymbol());
                    if (symbol == null) continue;

                    BigDecimal tradedCost = trade.getPrice().multiply(trade.getQuantity()).setScale(symbol.getQuoteScale(), RoundingMode.DOWN);
                    totalTradedQty = totalTradedQty.add(trade.getQuantity());
                    totalTradedCost = totalTradedCost.add(tradedCost);

                    // 【修改】传入 reason
                    String reason = "trade:" + trade.getBuyOrderId() + "/" + trade.getSellOrderId();
                    walletService.reduceFrozen(trade.getBuyerUserId(), symbol.getQuoteCoin(), tradedCost, reason);
                    walletService.reduceFrozen(trade.getSellerUserId(), symbol.getBaseCoin(), trade.getQuantity(), reason);
                    
                    walletService.settleCredit(trade.getBuyerUserId(), symbol.getBaseCoin(), trade.getQuantity(), reason);
                    walletService.settleCredit(trade.getSellerUserId(), symbol.getQuoteCoin(), tradedCost, reason);

                    Trade tradeEntity = Trade.builder()
                            .buyOrderId(trade.getBuyOrderId()).sellOrderId(trade.getSellOrderId())
                            .symbol(trade.getSymbol()).price(trade.getPrice()).quantity(trade.getQuantity())
                            .build();
                    pendingTrades.add(tradeEntity);

                    updateOrderStatus(trade.getBuyOrderId(), trade.getQuantity(), tradedCost);
                    updateOrderStatus(trade.getSellOrderId(), trade.getQuantity(), tradedCost);

                    eventPublisher.publishEvent(new TradeExecutedEvent(this, tradeEntity));
                } catch (Exception e) {
                    log.error("处理成交事件失败: trade={}", trade, e);
                }
            }

            try {
                OrderEntity incomingOrder = orderMapper.selectById(event.getOrderId());
                if (incomingOrder != null && incomingOrder.getType() == OrderTypes.OrderType.MARKET) {
                    if (totalTradedQty.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal avgPrice = totalTradedCost.divide(totalTradedQty, symbolService.getSymbol(incomingOrder.getSymbol()).getQuoteScale(), RoundingMode.HALF_UP);
                        incomingOrder.setPrice(avgPrice);
                        pendingOrderUpdates.put(incomingOrder.getId(), incomingOrder);
                    }
                }
            } catch (Exception e) {
                log.error("更新市价单均价失败: orderId={}", event.getOrderId(), e);
            }
        }

        try {
            OrderEntity finalOrderState = orderMapper.selectById(event.getOrderId());
            if (finalOrderState == null) return;

            boolean isTerminatedBySelfTrade = event.isSelfTradeCancel();
            boolean isMarketOrderAndNotFilled = finalOrderState.getType() == OrderTypes.OrderType.MARKET && !finalOrderState.isFullyFilled();

            if (isTerminatedBySelfTrade || isMarketOrderAndNotFilled) {
                String reason = isTerminatedBySelfTrade ? "Self-trade detected" : "Market order closed due to insufficient depth";
                
                if (finalOrderState.getType() == OrderTypes.OrderType.MARKET && finalOrderState.getSide() == OrderTypes.Side.BUY) {
                    finalOrderState.setAmount(finalOrderState.getFilled());
                }

                unfreezeRemainingFunds(finalOrderState, reason);
                
                if (finalOrderState.getFilled().compareTo(BigDecimal.ZERO) > 0) {
                    finalOrderState.setStatus(OrderStatus.PARTIALLY_FILLED_AND_CLOSED);
                } else {
                    finalOrderState.setStatus(OrderStatus.CANCELED);
                }
                pendingOrderUpdates.put(finalOrderState.getId(), finalOrderState);
            }
        } catch (Exception e) {
            log.error("处理订单最终状态失败: orderId={}", event.getOrderId(), e);
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
        pendingOrderUpdates.put(order.getId(), order);
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
        OrderEntity order = pendingOrderUpdates.computeIfAbsent(orderId, id -> orderMapper.selectById(id));
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
        pendingOrderUpdates.put(orderId, order);
    }

    private void flush() {
        if (!pendingTrades.isEmpty()) {
            try {
                tradeMapper.insertBatch(pendingTrades);
                log.info("批量插入 {} 条成交记录。", pendingTrades.size());
            } catch (Exception e) {
                log.error("批量插入成交记录失败。", e);
            } finally {
                pendingTrades.clear();
            }
        }
        if (!pendingOrderUpdates.isEmpty()) {
            try {
                List<OrderEntity> ordersToUpdate = new ArrayList<>(pendingOrderUpdates.values());
                orderMapper.updateBatch(ordersToUpdate);
                log.info("批量更新 {} 个订单状态。", ordersToUpdate.size());
            } catch (Exception e) {
                log.error("批量更新订单状态失败。", e);
            } finally {
                pendingOrderUpdates.clear();
            }
        }
    }
}
