package com.remus.dwanjcex.disruptor.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final PersistenceHandler self;

    // 【批处理优化】缓冲区
    private final List<Trade> pendingTrades = new ArrayList<>();
    private final Map<Long, OrderEntity> pendingOrderUpdates = new HashMap<>();
    private final List<Runnable> postCommitActions = new ArrayList<>();
    private static final int BATCH_SIZE = 100;

    public PersistenceHandler(WalletService walletService, OrderMapper orderMapper, TradeMapper tradeMapper, SymbolService symbolService, ApplicationEventPublisher eventPublisher, StringRedisTemplate redisTemplate, ObjectMapper objectMapper, @Lazy PersistenceHandler self) {
        this.walletService = walletService;
        this.orderMapper = orderMapper;
        this.tradeMapper = tradeMapper;
        this.symbolService = symbolService;
        this.eventPublisher = eventPublisher;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.self = self;
    }

    @Override
    public void onEvent(DisruptorEvent event, long sequence, boolean endOfBatch) throws Exception {
        try {
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

            // 【批处理优化】在批次结束或达到阈值时，执行批量提交
            if (endOfBatch || pendingTrades.size() >= BATCH_SIZE || pendingOrderUpdates.size() >= BATCH_SIZE) {
                self.flush(); // 调用带有事务的flush方法
            }
        } catch (Exception e) {
            log.error("PersistenceHandler 处理事件失败: event={}", event, e);
            // 发生异常时，尝试清空缓冲区以避免影响后续处理，或者根据策略重试
            pendingTrades.clear();
            pendingOrderUpdates.clear();
            postCommitActions.clear();
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
                    processSingleTrade(trade);
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

    // 【修改】移除@Transactional，改为累积到缓冲区
    public void processSingleTrade(TradeEvent trade) {
        SymbolEntity symbol = symbolService.getSymbol(trade.getSymbol());
        if (symbol == null) return;

        BigDecimal tradedCost = trade.getPrice().multiply(trade.getQuantity()).setScale(symbol.getQuoteScale(), RoundingMode.DOWN);
        String reason = "trade:" + trade.getBuyOrderId() + "/" + trade.getSellOrderId();

        // 资金结算仍然实时执行
        walletService.reduceFrozen(trade.getBuyerUserId(), symbol.getQuoteCoin(), tradedCost, reason);
        walletService.reduceFrozen(trade.getSellerUserId(), symbol.getBaseCoin(), trade.getQuantity(), reason);
        walletService.settleCredit(trade.getBuyerUserId(), symbol.getBaseCoin(), trade.getQuantity(), reason);
        walletService.settleCredit(trade.getSellerUserId(), symbol.getQuoteCoin(), tradedCost, reason);

        Trade tradeEntity = Trade.builder()
                .buyOrderId(trade.getBuyOrderId()).sellOrderId(trade.getSellOrderId())
                .symbol(trade.getSymbol()).price(trade.getPrice()).quantity(trade.getQuantity())
                .build();
        
        // 【修改】添加到缓冲区
        pendingTrades.add(tradeEntity);

        updateOrderStatus(trade.getBuyOrderId(), trade.getQuantity(), tradedCost);
        updateOrderStatus(trade.getSellOrderId(), trade.getQuantity(), tradedCost);

        // 【修改】将Redis推送添加到后置操作列表
        postCommitActions.add(() -> publishToRedis(tradeEntity));
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

    // 【修改】移除@Transactional，改为累积到缓冲区
    public void processCancelOrder(Long orderId, String reason) {
        // 从缓冲区或数据库获取订单
        OrderEntity order = pendingOrderUpdates.get(orderId);
        if (order == null) {
            order = orderMapper.selectById(orderId);
        }
        
        if (order == null || (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PARTIAL)) return;
        unfreezeRemainingFunds(order, reason);
        order.setStatus(OrderStatus.CANCELED);
        
        // 【修改】添加到缓冲区
        pendingOrderUpdates.put(order.getId(), order);
    }

    // 【修改】移除@Transactional，改为累积到缓冲区
    public void updateMarketOrderAvgPrice(Long orderId, BigDecimal totalTradedQty, BigDecimal totalTradedCost) {
        OrderEntity incomingOrder = pendingOrderUpdates.get(orderId);
        if (incomingOrder == null) {
            incomingOrder = orderMapper.selectById(orderId);
        }
        
        if (incomingOrder != null && incomingOrder.getType() == OrderTypes.OrderType.MARKET && totalTradedQty.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal avgPrice = totalTradedCost.divide(totalTradedQty, symbolService.getSymbol(incomingOrder.getSymbol()).getQuoteScale(), RoundingMode.HALF_UP);
            incomingOrder.setPrice(avgPrice);
            // 【修改】添加到缓冲区
            pendingOrderUpdates.put(incomingOrder.getId(), incomingOrder);
        }
    }

    // 【修改】移除@Transactional，改为累积到缓冲区
    public void processFinalOrderState(Long orderId, boolean isSelfTradeCancel) {
        OrderEntity finalOrderState = pendingOrderUpdates.get(orderId);
        if (finalOrderState == null) {
            finalOrderState = orderMapper.selectById(orderId);
        }
        
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
            // 【修改】添加到缓冲区
            pendingOrderUpdates.put(finalOrderState.getId(), finalOrderState);
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
        OrderEntity order = pendingOrderUpdates.get(orderId);
        if (order == null) {
            order = orderMapper.selectById(orderId);
        }
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
        // 【修改】添加到缓冲区
        pendingOrderUpdates.put(orderId, order);
    }

    /**
     * 【新增】批量提交方法，带有事务控制
     */
    @Transactional
    public void flush() {
        if (!pendingTrades.isEmpty()) {
            try {
                tradeMapper.insertBatch(pendingTrades);
                log.info("批量插入 {} 条成交记录。", pendingTrades.size());
            } catch (Exception e) {
                log.error("批量插入成交记录失败。", e);
                throw e; // 抛出异常以触发回滚
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
                throw e; // 抛出异常以触发回滚
            } finally {
                pendingOrderUpdates.clear();
            }
        }
        
        // 执行后置操作 (如Redis推送)
        // 注意：如果上面的数据库操作失败回滚，这些操作将不会被执行（因为抛出了异常）
        for (Runnable action : postCommitActions) {
            try {
                action.run();
            } catch (Exception e) {
                log.error("执行后置操作失败", e);
            }
        }
        postCommitActions.clear();
    }
}
