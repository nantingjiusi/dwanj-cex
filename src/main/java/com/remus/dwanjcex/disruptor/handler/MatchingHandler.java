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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MatchingHandler implements EventHandler<DisruptorEvent> {

    @Getter
    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MatchStrategyFactory strategyFactory;

    private final Map<String, Map<String, List<OrderBookLevel>>> snapshotCache = new ConcurrentHashMap<>();

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
                updateSnapshotAndPublish(symbolToUpdate, orderBook);
            }
        }
    }

    private String handlePlaceOrder(DisruptorEvent event) {
        OrderDto dto = event.getPlaceOrder();
        OrderBook orderBook = books.computeIfAbsent(dto.getSymbol(), OrderBook::new);
        OrderEntity order = OrderEntity.builder()
                .id(event.getOrderId())
                .userId(dto.getUserId())
                .symbol(dto.getSymbol())
                .type(dto.getType())
                .price(dto.getPrice())
                .amount(dto.getAmount())
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
        this.snapshotCache.put(symbol, displaySnapshot);

        try {
            // 1. 序列化活跃订单列表到Redis，用于重建
            List<OrderEntity> activeOrders = new ArrayList<>(orderBook.getOrderMap().values());
            String activeOrdersJson = objectMapper.writeValueAsString(activeOrders);
            redisTemplate.opsForValue().set("orderbook:snapshot:" + symbol, activeOrdersJson);

            // 2. 【修改】序列化用于显示的快照到Redis
            String displaySnapshotJson = objectMapper.writeValueAsString(displaySnapshot);
            redisTemplate.opsForValue().set("orderbook_display_snapshot:" + symbol, displaySnapshotJson);

            // 3. 向Redis频道发布订单簿更新
            redisTemplate.convertAndSend("channel:orderbook:" + symbol, displaySnapshotJson);

        } catch (JsonProcessingException e) {
            log.error("序列化或发布订单簿快照失败: symbol={}", symbol, e);
        }
    }
    
    public void rebuildCache(String symbol, OrderBook orderBook) {
        log.info("正在为 {} 重建快照缓存...", symbol);
        Map<String, List<OrderBookLevel>> snapshot = orderBook.getOrderBookSnapshot();
        this.snapshotCache.put(symbol, snapshot);
        
        // 【新增】在重建时也预热Redis中的显示快照
        try {
            String displaySnapshotJson = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set("orderbook_display_snapshot:" + symbol, displaySnapshotJson);
        } catch (JsonProcessingException e) {
            log.error("预热订单簿显示快照到Redis失败: symbol={}", symbol, e);
        }
    }

    public Map<String, List<OrderBookLevel>> getOrderBookSnapshot(String symbol) {
        return this.snapshotCache.get(symbol);
    }
}
