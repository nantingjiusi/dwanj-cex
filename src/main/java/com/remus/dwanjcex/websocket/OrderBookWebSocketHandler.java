package com.remus.dwanjcex.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remus.dwanjcex.disruptor.handler.MatchingHandler;
import com.remus.dwanjcex.wallet.entity.dto.OrderBookLevel;
import com.remus.dwanjcex.websocket.dto.WebSocketPushMessage;
import com.remus.dwanjcex.websocket.dto.WebSocketRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 处理订单簿WebSocket连接和消息的处理器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderBookWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketPushService pushService;
    private final MatchingHandler matchingHandler;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket连接已建立: {}", session.getId());
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
        for (String arg : request.getArgs()) {
            if ("subscribe".equals(op)) {
                // 订阅主题
                pushService.subscribe(arg, session);
                // 订阅后，立即发送一次最新的全量快照
                sendInitialSnapshot(session, arg);
            } else if ("unsubscribe".equals(op)) {
                // 取消订阅
                // pushService.unsubscribe(arg, session); // 如果需要按主题取消
            } else {
                log.warn("不支持的操作: {}", op);
            }
        }
    }

    /**
     * 发送初始的全量订单簿快照。
     */
    private void sendInitialSnapshot(WebSocketSession session, String topic) throws IOException {
        // topic格式为 "orderbook:BTCUSDT"
        String[] parts = topic.split(":");
        if (parts.length != 2 || !"orderbook".equals(parts[0])) {
            return;
        }
        String symbol = parts[1];

        // 从MatchingHandler的缓存中获取最新的快照
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
        // 清理该会话的所有订阅
        pushService.unsubscribe(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输错误: session {}, error: {}", session.getId(), exception.getMessage());
        session.close(CloseStatus.SERVER_ERROR);
    }
}
