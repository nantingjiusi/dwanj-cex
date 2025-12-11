package com.remus.dwanjcex.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remus.dwanjcex.wallet.entity.Trade;
import com.remus.dwanjcex.wallet.entity.dto.OrderBookLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMessageSubscriber implements MessageListener {

    private final WebSocketPushService webSocketPushService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());
        log.debug("收到Redis Pub/Sub消息: channel={}", channel);

        try {
            if (channel.startsWith("channel:orderbook:")) {
                String symbol = channel.substring("channel:orderbook:".length());
                Map<String, List<OrderBookLevel>> orderBookData = objectMapper.readValue(body, new TypeReference<>() {});
                webSocketPushService.broadcast("orderbook:" + symbol, orderBookData);

            } else if (channel.startsWith("channel:ticker:")) {
                String symbol = channel.substring("channel:ticker:".length());
                Trade trade = objectMapper.readValue(body, Trade.class);
                
                // 【关键修复】只更新缓存，不执行任何推送逻辑
                webSocketPushService.updateLastPrice(symbol, trade.getPrice());
            }
        } catch (IOException e) {
            log.error("处理Redis消息失败: channel={}, body={}", channel, body, e);
        }
    }
}
