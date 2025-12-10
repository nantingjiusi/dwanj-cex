package com.remus.dwanjcex.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remus.dwanjcex.config.jwt.JwtUtils;
import com.remus.dwanjcex.disruptor.handler.MatchingHandler;
import com.remus.dwanjcex.wallet.entity.dto.OrderBookLevel;
import com.remus.dwanjcex.websocket.dto.WebSocketPushMessage;
import com.remus.dwanjcex.websocket.dto.WebSocketRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderBookWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketPushService pushService;
    private final MatchingHandler matchingHandler;
    private final ObjectMapper objectMapper;
    private final JwtUtils jwtUtils;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket连接已建立 (匿名): {}", session.getId());
        // 连接建立时是匿名的，等待客户端发送auth请求
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("收到WebSocket消息: {} from session: {}", payload, session.getId());

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
        String arg = request.getArgs().get(0);

        switch (op) {
            case "subscribe":
                pushService.subscribe(arg, session);
                sendInitialSnapshot(session, arg);
                break;
            case "unsubscribe":
                // pushService.unsubscribe(arg, session); // 如果需要按主题取消
                break;
            case "auth":
                handleAuth(session, arg);
                break;
            default:
                log.warn("不支持的操作: {}", op);
                session.sendMessage(new TextMessage("{\"error\": \"Unsupported operation\"}"));
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
        String[] parts = topic.split(":");
        if (parts.length != 2 || !"orderbook".equals(parts[0])) {
            return;
        }
        String symbol = parts[1];

        Map<String, List<OrderBookLevel>> snapshot = matchingHandler.getOrderBookSnapshot(symbol);

        if (snapshot != null && !snapshot.isEmpty()) {
            WebSocketPushMessage<?> pushMessage = new WebSocketPushMessage<>(topic, snapshot);
            String payload = objectMapper.writeValueAsString(pushMessage);
            session.sendMessage(new TextMessage(payload));
            log.info("向 session {} 发送了 {} 的初始快照", session.getId(), symbol);
        }
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
