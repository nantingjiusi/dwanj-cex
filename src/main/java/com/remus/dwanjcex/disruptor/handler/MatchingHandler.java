package com.remus.dwanjcex.disruptor.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmax.disruptor.EventHandler;
import com.remus.dwanjcex.disruptor.event.DisruptorEvent;
import com.remus.dwanjcex.engine.OrderBook;
import com.remus.dwanjcex.engine.strategy.MatchStrategy;
import com.remus.dwanjcex.engine.strategy.MatchStrategyFactory;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.dto.CancelOrderDto;
import com.remus.dwanjcex.wallet.entity.dto.OrderBookLevel;
import com.remus.dwanjcex.wallet.entity.dto.OrderDto;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@Scope("prototype")
public class MatchingHandler implements EventHandler<DisruptorEvent> {

    @Getter
    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MatchStrategyFactory strategyFactory;

    private long lastSnapshotTime = 0;
    private static final long SNAPSHOT_INTERVAL_MS = 100;

    public MatchingHandler(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, MatchStrategyFactory strategyFactory) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.strategyFactory = strategyFactory;
    }

    @Override
    public void onEvent(DisruptorEvent event, long sequence, boolean endOfBatch) {
        String symbolToUpdate = null;
        switch (event.getType()) {
            case PLACE_ORDER:
                symbolToUpdate = handlePlaceOrder(event);
                break;
            case CANCEL_ORDER:
                symbolToUpdate = handleCancelOrder(event);
                break;
        }

        if (symbolToUpdate != null) {
            OrderBook orderBook = books.get(symbolToUpdate);
            if (orderBook != null) {
                long now = System.currentTimeMillis();
                if (endOfBatch || (now - lastSnapshotTime) >= SNAPSHOT_INTERVAL_MS) {
                    updateSnapshotAndPublish(symbolToUpdate, orderBook);
                    lastSnapshotTime = now;
                }
            }
        }
    }

    public void removeOrder(String symbol, Long orderId) {
        log.warn("收到强制移除指令，从内存订单簿中移除订单: {}", orderId);
        OrderBook orderBook = books.get(symbol);
        if (orderBook != null) {
            boolean removed = orderBook.remove(orderId);
            if (removed) {
                log.info("成功从内存中移除僵尸订单: {}", orderId);
                updateSnapshotAndPublish(symbol, orderBook);
            } else {
                log.warn("尝试移除僵尸订单 {} 失败，可能已被移除。", orderId);
            }
        }
    }

    private String handlePlaceOrder(DisruptorEvent event) {
        OrderDto dto = event.getPlaceOrder();
        OrderBook orderBook = books.computeIfAbsent(dto.getSymbol(), OrderBook::new);
        
        OrderEntity order = OrderEntity.builder()
                .id(event.getOrderId())
                .userId(dto.getUserId())
                .marketSymbol(dto.getSymbol())
                .type(dto.getType())
                .price(dto.getPrice())
                .quantity(dto.getAmount())
                .quoteAmount(dto.getQuoteAmount())
                .side(dto.getSide())
                .build();

        MatchStrategy strategy = strategyFactory.getStrategy(order.getType());
        strategy.match(order, orderBook, event);

        return dto.getSymbol();
    }

    private String handleCancelOrder(DisruptorEvent event) {
        CancelOrderDto dto = event.getCancelOrder();
        OrderBook orderBook = books.get(dto.getSymbol());
        if (orderBook == null) return null;
        if (orderBook.remove(dto.getOrderId())) {
            return dto.getSymbol();
        }
        return null;
    }

    private void updateSnapshotAndPublish(String symbol, OrderBook orderBook) {
        Map<String, List<OrderBookLevel>> displaySnapshot = orderBook.getOrderBookSnapshot();
        try {
            List<OrderEntity> activeOrders = new ArrayList<>(orderBook.getOrderMap().values());
            String activeOrdersJson = objectMapper.writeValueAsString(activeOrders);
            redisTemplate.opsForValue().set("orderbook:snapshot:" + symbol, activeOrdersJson);

            String displaySnapshotJson = objectMapper.writeValueAsString(displaySnapshot);
            String displayKey = "orderbook:display:snapshot:" + symbol;
            log.info("写入Redis Key: {}", displayKey);
            redisTemplate.opsForValue().set(displayKey, displaySnapshotJson);

            redisTemplate.convertAndSend("channel:orderbook:" + symbol, displaySnapshotJson);

        } catch (JsonProcessingException e) {
            log.error("序列化或发布订单簿快照失败: symbol={}", symbol, e);
        }
    }
    
    public void rebuildCache(String symbol, OrderBook orderBook) {
        log.info("正在为 {} 重建快照缓存...", symbol);
        try {
            Map<String, List<OrderBookLevel>> snapshot = orderBook.getOrderBookSnapshot();
            String displaySnapshotJson = objectMapper.writeValueAsString(snapshot);
            
            // 1. 写入缓存
            String displayKey = "orderbook:display:snapshot:" + symbol;
            redisTemplate.opsForValue().set(displayKey, displaySnapshotJson);
            
            // 2. 【关键修复】发布通知，告知所有在线用户
            redisTemplate.convertAndSend("channel:orderbook:" + symbol, displaySnapshotJson);
            log.info("已发布 {} 的重建快照到频道。", symbol);

        } catch (JsonProcessingException e) {
            log.error("预热订单簿显示快照到Redis失败: symbol={}", symbol, e);
        }
    }

    public Map<String, List<OrderBookLevel>> getOrderBookSnapshot(String symbol) {
        return null;
    }
}
