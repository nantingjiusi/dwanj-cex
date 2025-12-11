package com.remus.dwanjcex.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remus.dwanjcex.wallet.entity.Trade;
import com.remus.dwanjcex.websocket.dto.WebSocketPushMessage;
import com.remus.dwanjcex.websocket.event.OrderBookUpdateEvent;
import com.remus.dwanjcex.websocket.event.OrderCancelNotificationEvent;
import com.remus.dwanjcex.websocket.event.TradeExecutedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketPushService {

    private final ObjectMapper objectMapper;

    private final Map<String, Set<WebSocketSession>> subscriptions = new ConcurrentHashMap<>();
    private final Map<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    
    // 存储每个交易对的最新成交价
    private final Map<String, BigDecimal> lastPrices = new ConcurrentHashMap<>(); 
    // 存储上一次推送给前端的价格，用于去重和节流
    private final Map<String, BigDecimal> lastPushedPrices = new ConcurrentHashMap<>();

    @EventListener
    public void handleOrderBookUpdate(OrderBookUpdateEvent event) {
        String topic = "orderbook:" + event.getSymbol();
        // 订单簿更新通常频率较低（由Disruptor批处理控制），可以直接推送
        // 如果订单簿更新也非常频繁，也可以应用类似的节流逻辑
        broadcast(topic, event.getOrderBookData());
    }

    @EventListener
    public void handleTradeExecuted(TradeExecutedEvent event) {
        Trade trade = event.getTrade();
        // 【修改】只更新缓存，不直接推送
        lastPrices.put(trade.getSymbol(), trade.getPrice());
    }

    /**
     * 定时任务：每100ms检查一次是否有新的价格需要推送。
     * 实现了推送节流，避免前端因高频更新而卡顿。
     */
    @Scheduled(fixedRate = 100)
    public void pushTickerUpdates() {
        lastPrices.forEach((symbol, currentPrice) -> {
            BigDecimal lastPushed = lastPushedPrices.get(symbol);
            
            // 只有当价格发生变化，或者从未推送过时，才进行推送
            if (lastPushed == null || currentPrice.compareTo(lastPushed) != 0) {
                String topic = "ticker:" + symbol;
                broadcast(topic, currentPrice);
                lastPushedPrices.put(symbol, currentPrice);
                // log.debug("推送了 {} 的最新价格: {}", symbol, currentPrice);
            }
        });
    }

    @EventListener
    public void handleOrderCancelNotification(OrderCancelNotificationEvent event) {
        Long userId = event.getUserId();
        String topic = "private:" + userId;
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> data = Map.of(
                        "type", "orderUpdate",
                        "orderId", event.getOrderId(),
                        "status", "CANCELED",
                        "reason", event.getReason()
                );
                sendMessage(session, topic, data);
            } catch (IOException e) {
                log.error("向session {} 发送私有消息失败: {}", session.getId(), e.getMessage());
            }
        }
    }

    public void subscribe(String topic, WebSocketSession session) {
        subscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("Session {} 订阅了公共主题: {}", session.getId(), topic);
    }

    public void registerUserSession(Long userId, WebSocketSession session) {
        userSessions.put(userId, session);
        session.getAttributes().put("userId", userId);
        log.info("Session {} 已注册为用户 {}", session.getId(), userId);
    }

    public void unsubscribe(WebSocketSession session) {
        subscriptions.forEach((topic, subscribers) -> {
            if (subscribers.remove(session)) {
                log.info("Session {} 取消订阅了公共主题: {}", session.getId(), topic);
            }
        });
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId != null) {
            userSessions.remove(userId, session);
            log.info("Session {} (用户 {}) 已注销。", session.getId(), userId);
        }
    }

    public BigDecimal getLastPrice(String symbol) {
        return lastPrices.getOrDefault(symbol, BigDecimal.ZERO);
    }

    private void broadcast(String topic, Object data) {
        Set<WebSocketSession> subscribers = subscriptions.get(topic);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(new WebSocketPushMessage<>(topic, data));
            TextMessage textMessage = new TextMessage(payload);
            for (WebSocketSession session : subscribers) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        log.error("向session {} 广播消息失败: {}", session.getId(), e.getMessage());
                    }
                }
            }
        } catch (JsonProcessingException e) {
            log.error("序列化广播消息失败: {}", e.getMessage());
        }
    }

    private void sendMessage(WebSocketSession session, String topic, Object data) throws IOException {
        String payload = objectMapper.writeValueAsString(new WebSocketPushMessage<>(topic, data));
        session.sendMessage(new TextMessage(payload));
    }
}
