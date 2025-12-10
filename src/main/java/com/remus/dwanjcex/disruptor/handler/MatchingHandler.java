package com.remus.dwanjcex.disruptor.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmax.disruptor.EventHandler;
import com.remus.dwanjcex.disruptor.event.DisruptorEvent;
import com.remus.dwanjcex.engine.OrderBook;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.dto.CancelOrderDto;
import com.remus.dwanjcex.wallet.entity.dto.OrderBookLevel;
import com.remus.dwanjcex.wallet.entity.dto.OrderDto;
import com.remus.dwanjcex.websocket.event.OrderBookUpdateEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MatchingHandler implements EventHandler<DisruptorEvent> {

    @Getter
    private final Map<String, OrderBook> books = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private volatile Map<String, Map<String, List<OrderBookLevel>>> snapshotCache = new ConcurrentHashMap<>();

    public MatchingHandler(ApplicationEventPublisher eventPublisher, StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.eventPublisher = eventPublisher;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
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
            default:
                log.warn("未知的事件类型: {}", event.getType());
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
                .price(dto.getPrice())
                .amount(dto.getAmount())
                .side(dto.getSide())
                .build();
        orderBook.match(order, event);
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
        Map<String, List<OrderBookLevel>> snapshot = orderBook.getOrderBookSnapshot();
        this.snapshotCache.put(symbol, snapshot);

        try {
            String snapshotJson = objectMapper.writeValueAsString(orderBook);
            redisTemplate.opsForValue().set("orderbook:snapshot:" + symbol, snapshotJson);
        } catch (JsonProcessingException e) {
            log.error("序列化订单簿快照到Redis失败: symbol={}", symbol, e);
        }

        eventPublisher.publishEvent(new OrderBookUpdateEvent(this, symbol, snapshot));
    }

    /**
     * 在启动时，根据重建的OrderBook来重建快照缓存。
     * @param symbol 交易对
     * @param orderBook 重建的订单簿对象
     */
    public void rebuildCache(String symbol, OrderBook orderBook) {
        log.info("正在为 {} 重建快照缓存...", symbol);
        Map<String, List<OrderBookLevel>> snapshot = orderBook.getOrderBookSnapshot();
        this.snapshotCache.put(symbol, snapshot);
    }

    public Map<String, List<OrderBookLevel>> getOrderBookSnapshot(String symbol) {
        return this.snapshotCache.get(symbol);
    }
}
