package com.remus.dwanjcex.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remus.dwanjcex.websocket.dto.WebSocketPushMessage;
import com.remus.dwanjcex.websocket.event.OrderBookUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 负责通过WebSocket向客户端推送消息。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/18 20:45
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketPushService {

    private final ObjectMapper objectMapper;

    /**
     * 存储订阅关系：topic -> Set<WebSocketSession>
     * 例如: "orderbook:BTCUSDT" -> {session1, session2, ...}
     */
    private final Map<String, Set<WebSocketSession>> subscriptions = new ConcurrentHashMap<>();

    /**
     * 监听订单簿更新事件，并向订阅者推送。
     * @param event 订单簿更新事件
     */
    @EventListener
    public void handleOrderBookUpdate(OrderBookUpdateEvent event) {
        String topic = "orderbook:" + event.getSymbol();
        log.debug("处理订单簿更新事件，主题: {}", topic);

        Set<WebSocketSession> subscribers = subscriptions.get(topic);
        if (subscribers == null || subscribers.isEmpty()) {
            return; // 没有订阅者
        }

        try {
            WebSocketPushMessage<?> pushMessage = new WebSocketPushMessage<>(topic, event.getOrderBookData());
            String payload = objectMapper.writeValueAsString(pushMessage);
            TextMessage textMessage = new TextMessage(payload);

            // 向所有订阅者广播消息
            for (WebSocketSession session : subscribers) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        log.error("向session {} 发送消息失败: {}", session.getId(), e.getMessage());
                    }
                }
            }
        } catch (JsonProcessingException e) {
            log.error("序列化WebSocket推送消息失败: {}", e.getMessage());
        }
    }

    /**
     * 注册一个订阅。
     * @param topic   主题
     * @param session WebSocket会话
     */
    public void subscribe(String topic, WebSocketSession session) {
        subscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("Session {} 订阅了主题: {}", session.getId(), topic);
    }

    /**
     * 取消一个会话的所有订阅。
     * @param session WebSocket会话
     */
    public void unsubscribe(WebSocketSession session) {
        subscriptions.forEach((topic, subscribers) -> {
            if (subscribers.remove(session)) {
                log.info("Session {} 取消订阅了主题: {}", session.getId(), topic);
            }
        });
    }
}
