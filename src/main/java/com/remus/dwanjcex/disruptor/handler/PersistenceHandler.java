package com.remus.dwanjcex.disruptor.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmax.disruptor.EventHandler;
import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.disruptor.event.DisruptorEvent;
import com.remus.dwanjcex.disruptor.event.TradeEvent;
import com.remus.dwanjcex.disruptor.service.DisruptorManager;
import com.remus.dwanjcex.wallet.entity.Market;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.Trade;
import com.remus.dwanjcex.wallet.entity.dto.CancelOrderDto;
import com.remus.dwanjcex.wallet.mapper.OrderMapper;
import com.remus.dwanjcex.wallet.mapper.TradeMapper;
import com.remus.dwanjcex.wallet.services.MarketService;
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
    private final MarketService marketService;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DisruptorManager disruptorManager;
    private final TransactionTemplate transactionTemplate;

    private final List<Trade> pendingTrades = new ArrayList<>();
    private final Map<Long, OrderEntity> pendingOrderUpdates = new HashMap<>();
    private final List<Trade> pendingRedisPushes = new ArrayList<>();
    private static final int BATCH_SIZE = 100;
    
    private long lastFlushTime = System.currentTimeMillis();
    private static final long FLUSH_INTERVAL_MS = 200;

    public PersistenceHandler(WalletService walletService, OrderMapper orderMapper, TradeMapper tradeMapper, MarketService marketService, ApplicationEventPublisher eventPublisher, StringRedisTemplate redisTemplate, ObjectMapper objectMapper, DisruptorManager disruptorManager, PlatformTransactionManager transactionManager) {
        this.walletService = walletService;
        this.orderMapper = orderMapper;
        this.tradeMapper = tradeMapper;
        this.marketService = marketService;
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

            long now = System.currentTimeMillis();
            boolean timeToFlush = (now - lastFlushTime) >= FLUSH_INTERVAL_MS;
            boolean bufferFull = pendingTrades.size() >= BATCH_SIZE || pendingOrderUpdates.size() >= BATCH_SIZE;
            boolean hasDataToFlush = !pendingTrades.isEmpty() || !pendingOrderUpdates.isEmpty();

            if ((endOfBatch || timeToFlush || bufferFull) && hasDataToFlush) {
                flush();
                lastFlushTime = now;
            }
        } catch (Exception e) {
            log.error("PersistenceHandler 处理事件失败: event={}", event, e);
            clearBuffers();
        }
    }

    private void handlePlaceOrderPersistence(DisruptorEvent event) {
        OrderEntity order = getOrderFromCacheOrDb(event.getOrderId());
        if (order == null) return;
        order.init(); // 初始化状态

        if (event.getCancelledOrderIds() != null && !event.getCancelledOrderIds().isEmpty()) {
            for (Long cancelledOrderId : event.getCancelledOrderIds()) {
                processCancelOrder(cancelledOrderId, "Cancelled by self-trade");
            }
        }

        List<TradeEvent> trades = event.getTradeEvents();
        if (trades != null && !trades.isEmpty()) {
            for (TradeEvent trade : trades) {
                processSingleTrade(trade);
            }
        }
        processFinalOrderState(order, event.isSelfTradeCancel());
    }

    private void handleCancelOrderPersistence(DisruptorEvent event) {
        CancelOrderDto dto = event.getCancelOrder();
        processCancelOrder(dto.getOrderId(), "Cancelled by user");
    }

    public void processSingleTrade(TradeEvent trade) {
        Market market = marketService.getMarket(trade.getSymbol());
        if (market == null) return;

        BigDecimal tradedCost = trade.getPrice().multiply(trade.getQuantity()).setScale(market.getPricePrecision(), RoundingMode.DOWN);
        String reason = "trade:" + trade.getTakerOrderId() + "/" + trade.getMakerOrderId();

        walletService.reduceFrozen(trade.getTakerUserId(), market.getQuoteAsset(), tradedCost, reason);
        walletService.reduceFrozen(trade.getMakerUserId(), market.getBaseAsset(), trade.getQuantity(), reason);
        walletService.settleCredit(trade.getTakerUserId(), market.getBaseAsset(), trade.getQuantity(), reason);
        walletService.settleCredit(trade.getMakerUserId(), market.getQuoteAsset(), tradedCost, reason);

        Trade tradeEntity = Trade.builder()
                .marketSymbol(trade.getSymbol())
                .price(trade.getPrice())
                .quantity(trade.getQuantity())
                .takerOrderId(trade.getTakerOrderId())
                .makerOrderId(trade.getMakerOrderId())
                .takerUserId(trade.getTakerUserId())
                .makerUserId(trade.getMakerUserId())
                .fee(BigDecimal.ZERO)
                .build();
        
        pendingTrades.add(tradeEntity);
        pendingRedisPushes.add(tradeEntity);

        // 【关键修改】使用状态模式更新订单状态
        OrderEntity takerOrder = getOrderFromCacheOrDb(trade.getTakerOrderId());
        OrderEntity makerOrder = getOrderFromCacheOrDb(trade.getMakerOrderId());
        takerOrder.fill(trade.getQuantity(), tradedCost);
        makerOrder.fill(trade.getQuantity(), tradedCost);
        pendingOrderUpdates.put(takerOrder.getId(), takerOrder);
        pendingOrderUpdates.put(makerOrder.getId(), makerOrder);
    }

    public void processCancelOrder(Long orderId, String reason) {
        OrderEntity order = getOrderFromCacheOrDb(orderId);
        if (order == null) return;
        
        unfreezeRemainingFunds(order, reason);
        order.cancel(); // 【关键修改】
        pendingOrderUpdates.put(order.getId(), order);
    }

    public void processFinalOrderState(OrderEntity order, boolean isSelfTradeCancel) {
        boolean isMarketOrderAndNotFilled = order.getType() == OrderTypes.OrderType.MARKET && !order.isFullyFilled();

        if (isSelfTradeCancel || isMarketOrderAndNotFilled) {
            String reason = isSelfTradeCancel ? "Self-trade detected" : "Market order closed due to insufficient depth";
            unfreezeRemainingFunds(order, reason);
            order.cancel(); // 【关键修改】
            pendingOrderUpdates.put(order.getId(), order);

            if (isSelfTradeCancel) {
                log.warn("通过DisruptorManager强制从内存中移除订单: {}", order.getId());
                disruptorManager.getMatchingHandler(order.getMarketSymbol()).removeOrder(order.getMarketSymbol(), order.getId());
            }
        }
    }

    private void unfreezeRemainingFunds(OrderEntity order, String reason) {
        Market market = marketService.getMarket(order.getMarketSymbol());
        if (market == null) return;

        if (order.getSide() == OrderTypes.Side.BUY) {
            BigDecimal amountToUnfreeze;
            if (order.getType() == OrderTypes.OrderType.LIMIT) {
                BigDecimal remainingQty = order.getQuantity().subtract(order.getFilled());
                amountToUnfreeze = order.getPrice().multiply(remainingQty).setScale(market.getPricePrecision(), RoundingMode.HALF_UP);
            } else {
                amountToUnfreeze = order.getQuoteAmount().subtract(order.getQuoteFilled());
            }
            if (amountToUnfreeze.compareTo(BigDecimal.ZERO) > 0) {
                walletService.unfreeze(order.getUserId(), market.getQuoteAsset(), amountToUnfreeze, reason + ":" + order.getId());
            }
        } else {
            BigDecimal remainingQty = order.getQuantity().subtract(order.getFilled());
            if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                walletService.unfreeze(order.getUserId(), market.getBaseAsset(), remainingQty, reason + ":" + order.getId());
            }
        }
        eventPublisher.publishEvent(new OrderCancelNotificationEvent(this, order.getUserId(), order.getId(), reason));
    }

    private OrderEntity getOrderFromCacheOrDb(Long orderId) {
        OrderEntity order = pendingOrderUpdates.get(orderId);
        if (order == null) {
            order = orderMapper.selectById(orderId);
            if (order != null) {
                order.init(); // 从数据库加载后，初始化状态
            }
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
        }
        
        if (!pendingRedisPushes.isEmpty()) {
            for (Trade trade : pendingRedisPushes) {
                publishToRedis(trade);
            }
        }
        
        clearBuffers();
    }
    
    private void clearBuffers() {
        pendingTrades.clear();
        pendingOrderUpdates.clear();
        pendingRedisPushes.clear();
    }

    private void publishToRedis(Trade tradeEntity) {
        try {
            String payload = objectMapper.writeValueAsString(tradeEntity);
            redisTemplate.convertAndSend("channel:ticker:" + tradeEntity.getMarketSymbol(), payload);
            redisTemplate.opsForValue().set("last_price:" + tradeEntity.getMarketSymbol(), tradeEntity.getPrice().toPlainString());
        } catch (Exception e) {
            log.error("发布成交记录到Redis失败", e);
        }
    }
}
