package com.remus.dwanjcex.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remus.dwanjcex.websocket.dto.WebSocketPushMessage;
import com.remus.dwanjcex.websocket.event.OrderCancelNotificationEvent;
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
    
    private final Map<String, BigDecimal> lastPrices = new ConcurrentHashMap<>(); 
    private final Map<String, BigDecimal> lastPushedPrices = new ConcurrentHashMap<>();

    public void updateLastPrice(String symbol, BigDecimal price) {
        this.lastPrices.put(symbol, price);
    }

    /**
     * 【关键修复】恢复定时任务，作为唯一的推送者
     */
    @Scheduled(fixedRate = 200) // 使用200ms以获得更平滑的体验
    public void pushTickerUpdates() {
        if (lastPrices.isEmpty()) {
            return;
        }

        lastPrices.forEach((symbol, currentPrice) -> {
            BigDecimal lastPushed = lastPushedPrices.get(symbol);
            
            if (lastPushed == null || currentPrice.compareTo(lastPushed) != 0) {
                String topic = "ticker:" + symbol;
                broadcast(topic, currentPrice);
                lastPushedPrices.put(symbol, currentPrice);
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
        return lastPrices.get(symbol);
    }

    public void broadcast(String topic, Object data) {
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
                        synchronized (session) {
                            session.sendMessage(textMessage);
                        }
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
        TextMessage textMessage = new TextMessage(payload);
        synchronized (session) {
            session.sendMessage(textMessage);
        }
    }
}
