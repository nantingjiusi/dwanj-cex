package com.remus.dwanjcex.disruptor.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmax.disruptor.EventHandler;
import com.remus.dwanjcex.common.OrderStatus;
import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.disruptor.event.DisruptorEvent;
import com.remus.dwanjcex.disruptor.event.TradeEvent;
import com.remus.dwanjcex.disruptor.service.DisruptorManager;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.SymbolEntity;
import com.remus.dwanjcex.wallet.entity.Trade;
import com.remus.dwanjcex.wallet.entity.dto.CancelOrderDto;
import com.remus.dwanjcex.wallet.mapper.OrderMapper;
import com.remus.dwanjcex.wallet.mapper.TradeMapper;
import com.remus.dwanjcex.wallet.services.SymbolService;
import com.remus.dwanjcex.wallet.services.WalletService;
import com.remus.dwanjcex.websocket.event.OrderCancelNotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Scope("prototype")
public class PersistenceHandler implements EventHandler<DisruptorEvent> {

    private final WalletService walletService;
    private final OrderMapper orderMapper;
    private final TradeMapper tradeMapper;
    private final SymbolService symbolService;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DisruptorManager disruptorManager;
    private final TransactionTemplate transactionTemplate;

    private final List<Trade> pendingTrades = new ArrayList<>();
    private final Map<Long, OrderEntity> pendingOrderUpdates = new HashMap<>();
    private final List<Trade> pendingRedisPushes = new ArrayList<>();
    private static final int BATCH_SIZE = 100;

    public PersistenceHandler(WalletService walletService, OrderMapper orderMapper, TradeMapper tradeMapper, SymbolService symbolService, ApplicationEventPublisher eventPublisher, StringRedisTemplate redisTemplate, ObjectMapper objectMapper, DisruptorManager disruptorManager, PlatformTransactionManager transactionManager) {
        this.walletService = walletService;
        this.orderMapper = orderMapper;
        this.tradeMapper = tradeMapper;
        this.symbolService = symbolService;
        this.eventPublisher = eventPublisher;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.disruptorManager = disruptorManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void onEvent(DisruptorEvent event, long sequence, boolean endOfBatch) {
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
            pendingTrades.clear();
            pendingOrderUpdates.clear();
            pendingRedisPushes.clear();
        }
    }

    private void handlePlaceOrderPersistence(DisruptorEvent event) {
        if (event.getCancelledOrderIds() != null && !event.getCancelledOrderIds().isEmpty()) {
            for (Long cancelledOrderId : event.getCancelledOrderIds()) {
                try {
                    processCancelOrder(cancelledOrderId, "Cancelled by self-trade");
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
                    Trade tradeEntity = processSingleTrade(trade);
                    if (tradeEntity != null) {
                        pendingRedisPushes.add(tradeEntity);
                    }
                    SymbolEntity symbol = symbolService.getSymbol(trade.getSymbol());
                    if (symbol != null) {
                        BigDecimal tradedCost = trade.getPrice().multiply(trade.getQuantity()).setScale(symbol.getQuoteScale(), RoundingMode.DOWN);
                        totalTradedQty = totalTradedQty.add(trade.getQuantity());
                        totalTradedCost = totalTradedCost.add(tradedCost);
                    }
                } catch (Exception e) {
                    log.error("处理成交事件失败: trade={}", trade, e);
                }
            }

            try {
                updateMarketOrderAvgPrice(event.getOrderId(), totalTradedQty, totalTradedCost);
            } catch (Exception e) {
                log.error("更新市价单均价失败: orderId={}", event.getOrderId(), e);
            }
        }

        try {
            processFinalOrderState(event.getOrderId(), event.isSelfTradeCancel());
        } catch (Exception e) {
            log.error("处理订单最终状态失败: orderId={}", event.getOrderId(), e);
        }
    }

    private void handleCancelOrderPersistence(DisruptorEvent event) {
        CancelOrderDto dto = event.getCancelOrder();
        try {
            processCancelOrder(dto.getOrderId(), "Cancelled by user");
        } catch (Exception e) {
            log.error("处理用户取消订单失败: orderId={}", dto.getOrderId(), e);
        }
    }

    public Trade processSingleTrade(TradeEvent trade) {
        SymbolEntity symbol = symbolService.getSymbol(trade.getSymbol());
        if (symbol == null) return null;

        BigDecimal tradedCost = trade.getPrice().multiply(trade.getQuantity()).setScale(symbol.getQuoteScale(), RoundingMode.DOWN);
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
        
        return tradeEntity;
    }

    public void processCancelOrder(Long orderId, String reason) {
        OrderEntity order = getOrderFromCacheOrDb(orderId);
        if (order == null || (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PARTIAL)) return;
        unfreezeRemainingFunds(order, reason);
        order.setStatus(OrderStatus.CANCELED);
        pendingOrderUpdates.put(order.getId(), order);
    }

    public void updateMarketOrderAvgPrice(Long orderId, BigDecimal totalTradedQty, BigDecimal totalTradedCost) {
        OrderEntity incomingOrder = getOrderFromCacheOrDb(orderId);
        if (incomingOrder != null && incomingOrder.getType() == OrderTypes.OrderType.MARKET && totalTradedQty.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal avgPrice = totalTradedCost.divide(totalTradedQty, symbolService.getSymbol(incomingOrder.getSymbol()).getQuoteScale(), RoundingMode.HALF_UP);
            incomingOrder.setPrice(avgPrice);
            pendingOrderUpdates.put(incomingOrder.getId(), incomingOrder);
        }
    }

    public void processFinalOrderState(Long orderId, boolean isSelfTradeCancel) {
        OrderEntity finalOrderState = getOrderFromCacheOrDb(orderId);
        if (finalOrderState == null) return;

        boolean isMarketOrderAndNotFilled = finalOrderState.getType() == OrderTypes.OrderType.MARKET && !finalOrderState.isFullyFilled();

        if (isSelfTradeCancel || isMarketOrderAndNotFilled) {
            String reason = isSelfTradeCancel ? "Self-trade detected" : "Market order closed due to insufficient depth";
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

            if (isSelfTradeCancel) {
                log.warn("通过DisruptorManager强制从内存中移除订单: {}", orderId);
                // 【关键修复】调用正确的方法名 removeOrder
                disruptorManager.getMatchingHandler(finalOrderState.getSymbol()).removeOrder(finalOrderState.getSymbol(), orderId);
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
            } else {
                amountToUnfreeze = order.getQuoteAmount().subtract(order.getQuoteFilled());
            }
            if (amountToUnfreeze.compareTo(BigDecimal.ZERO) > 0) {
                walletService.unfreeze(order.getUserId(), symbol.getQuoteCoin(), amountToUnfreeze, reason + ":" + order.getId());
            }
        } else {
            BigDecimal remainingQty = order.getAmount().subtract(order.getFilled());
            if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                walletService.unfreeze(order.getUserId(), symbol.getBaseCoin(), remainingQty, reason + ":" + order.getId());
            }
        }
        eventPublisher.publishEvent(new OrderCancelNotificationEvent(this, order.getUserId(), order.getId(), reason));
    }

    private void updateOrderStatus(Long orderId, BigDecimal tradedQty, BigDecimal tradedCost) {
        OrderEntity order = getOrderFromCacheOrDb(orderId);
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

    private OrderEntity getOrderFromCacheOrDb(Long orderId) {
        OrderEntity order = pendingOrderUpdates.get(orderId);
        if (order == null) {
            order = orderMapper.selectById(orderId);
        }
        return order;
    }

    public void flush() {
        if (!pendingTrades.isEmpty() || !pendingOrderUpdates.isEmpty()) {
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    try {
                        if (!pendingTrades.isEmpty()) {
                            tradeMapper.insertBatch(pendingTrades);
                            log.info("批量插入 {} 条成交记录。", pendingTrades.size());
                        }
                        if (!pendingOrderUpdates.isEmpty()) {
                            List<OrderEntity> ordersToUpdate = new ArrayList<>(pendingOrderUpdates.values());
                            orderMapper.updateBatch(ordersToUpdate);
                            log.info("批量更新 {} 个订单状态。", ordersToUpdate.size());
                        }
                    } catch (Exception e) {
                        log.error("批量提交数据库失败，回滚事务。", e);
                        status.setRollbackOnly();
                        throw e;
                    }
                }
            });
            
            pendingTrades.clear();
            pendingOrderUpdates.clear();
        }
        
        if (!pendingRedisPushes.isEmpty()) {
            for (Trade trade : pendingRedisPushes) {
                publishToRedis(trade);
            }
            pendingRedisPushes.clear();
        }
    }

    private void publishToRedis(Trade tradeEntity) {
        try {
            String payload = objectMapper.writeValueAsString(tradeEntity);
            redisTemplate.convertAndSend("channel:ticker:" + tradeEntity.getSymbol(), payload);
            redisTemplate.opsForValue().set("last_price:" + tradeEntity.getSymbol(), tradeEntity.getPrice().toPlainString());
        } catch (Exception e) {
            log.error("发布成交记录到Redis失败", e);
        }
    }
}
