package com.remus.dwanjcex.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remus.dwanjcex.config.jwt.JwtUtils;
import com.remus.dwanjcex.wallet.entity.dto.OrderBookLevel;
import com.remus.dwanjcex.websocket.dto.WebSocketPushMessage;
import com.remus.dwanjcex.websocket.dto.WebSocketRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderBookWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketPushService pushService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JwtUtils jwtUtils;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket连接已建立 (匿名): {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        // log.info("收到WebSocket消息: {} from session: {}", payload, session.getId());

        try {
            WebSocketRequest request = objectMapper.readValue(payload, WebSocketRequest.class);
            processRequest(session, request);
        } catch (JsonProcessingException e) {
            log.warn("无效的WebSocket请求格式: {}", payload, e);
            session.sendMessage(new TextMessage("{\"error\": \"Invalid JSON format\"}"));
        }
    }

    private void processRequest(WebSocketSession session, WebSocketRequest request) throws IOException {
        if (request.getOp() == null || request.getArgs() == null || request.getArgs().isEmpty()) {
            session.sendMessage(new TextMessage("{\"error\": \"Invalid request, op and args are required\"}"));
            return;
        }

        String op = request.getOp().toLowerCase();
        for (String arg : request.getArgs()) {
            switch (op) {
                case "subscribe":
                    pushService.subscribe(arg, session);
                    if (arg.startsWith("orderbook:")) {
                        sendInitialSnapshot(session, arg);
                    } else if (arg.startsWith("ticker:")) {
                        sendInitialTicker(session, arg);
                    }
                    break;
                case "unsubscribe":
                    pushService.unsubscribeTopic(arg, session); // 【新增】处理取消订阅
                    break;
                case "auth":
                    handleAuth(session, arg);
                    break;
                default:
                    log.warn("不支持的操作: {}", op);
                    session.sendMessage(new TextMessage("{\"error\": \"Unsupported operation\"}"));
            }
        }
    }

    private void handleAuth(WebSocketSession session, String token) throws IOException {
        if (StringUtils.hasText(token) && jwtUtils.validateToken(token)) {
            try {
                Long userId = jwtUtils.getUserIdFromToken(token);
                pushService.registerUserSession(userId, session);
                session.sendMessage(new TextMessage("{\"event\": \"auth\", \"status\": \"success\"}"));
            } catch (Exception e) {
                log.warn("WebSocket认证失败：无效的Token", e);
                session.sendMessage(new TextMessage("{\"event\": \"auth\", \"status\": \"failed\", \"message\": \"Invalid token\"}"));
            }
        } else {
            session.sendMessage(new TextMessage("{\"event\": \"auth\", \"status\": \"failed\", \"message\": \"Token is missing or invalid\"}"));
        }
    }

    private void sendInitialSnapshot(WebSocketSession session, String topic) throws IOException {
        String symbol = topic.substring("orderbook:".length());
        String redisKey = "orderbook_display_snapshot:" + symbol;
        String snapshotJson = redisTemplate.opsForValue().get(redisKey);
        
        if (snapshotJson != null) {
            log.info("从Redis获取到 {} 的初始订单簿快照，准备发送给 session {}", symbol, session.getId());
            Map<String, List<OrderBookLevel>> snapshot = objectMapper.readValue(snapshotJson, new TypeReference<>() {});
            sendMessage(session, topic, snapshot);
        } else {
            log.warn("Redis中不存在 {} 的订单簿快照 (Key: {})，无法发送初始数据。", symbol, redisKey);
        }
    }

    private void sendInitialTicker(WebSocketSession session, String topic) throws IOException {
        String symbol = topic.substring("ticker:".length());
        String redisKey = "last_price:" + symbol;
        String priceStr = redisTemplate.opsForValue().get(redisKey);
        
        if (priceStr != null) {
            BigDecimal lastPrice = new BigDecimal(priceStr);
            log.info("从Redis获取到 {} 的初始价格: {}，准备发送给 session {}", symbol, lastPrice, session.getId());
            sendMessage(session, topic, lastPrice);
        } else {
            log.warn("Redis中不存在 {} 的最新价格 (Key: {})，无法发送初始数据。", symbol, redisKey);
        }
    }

    private void sendMessage(WebSocketSession session, String topic, Object data) throws IOException {
        String payload = objectMapper.writeValueAsString(new WebSocketPushMessage<>(topic, data));
        session.sendMessage(new TextMessage(payload));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket连接已关闭: {}, 状态: {}", session.getId(), status);
        pushService.unsubscribe(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输错误: session {}, error: {}", session.getId(), exception.getMessage());
        session.close(CloseStatus.SERVER_ERROR);
    }
}
