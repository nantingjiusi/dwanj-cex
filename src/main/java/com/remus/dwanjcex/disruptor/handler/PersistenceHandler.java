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
import com.remus.dwanjcex.websocket.event.OrderForceRemovedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final List<Trade> pendingRedisPushes = new ArrayList<>(); // 待推送的Redis消息
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
                self.flush();
            }
        } catch (Exception e) {
            log.error("PersistenceHandler 处理事件失败: event={}", event, e);
            // 发生异常时，清空缓冲区，避免脏数据影响后续批次
            // 注意：这会导致当前批次中尚未提交的数据丢失，但在高并发容错场景下，
            // 优先保证系统不挂掉是首要任务。更完善的方案是将失败的事件发送到DLQ。
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
                        // 将待推送的Trade对象加入缓冲区
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

    // 【修改】移除@Transactional，改为累积到缓冲区
    public Trade processSingleTrade(TradeEvent trade) {
        SymbolEntity symbol = symbolService.getSymbol(trade.getSymbol());
        if (symbol == null) return null;

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
        
        return tradeEntity;
    }

    // 【修改】移除@Transactional，改为累积到缓冲区
    public void processCancelOrder(Long orderId, String reason) {
        OrderEntity order = pendingOrderUpdates.get(orderId);
        if (order == null) {
            order = orderMapper.selectById(orderId);
        }
        if (order == null || (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PARTIAL)) return;
        unfreezeRemainingFunds(order, reason);
        order.setStatus(OrderStatus.CANCELED);
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
            pendingOrderUpdates.put(finalOrderState.getId(), finalOrderState);

            if (isSelfTradeCancel) {
                log.warn("发布 OrderForceRemovedEvent 事件，强制从内存中移除订单: {}", orderId);
                eventPublisher.publishEvent(new OrderForceRemovedEvent(this, finalOrderState.getSymbol(), orderId));
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
                throw e;
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
                throw e;
            } finally {
                pendingOrderUpdates.clear();
            }
        }
        
        // 【关键修复】注册事务同步回调，在事务提交后推送Redis
        if (!pendingRedisPushes.isEmpty()) {
            List<Trade> tradesToPush = new ArrayList<>(pendingRedisPushes);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (Trade trade : tradesToPush) {
                        publishToRedis(trade);
                    }
                }
            });
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
