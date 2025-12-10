package com.remus.dwanjcex.config;

import com.remus.dwanjcex.websocket.OrderBookWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket相关配置。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/18 20:10
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final OrderBookWebSocketHandler orderBookWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(orderBookWebSocketHandler, "/ws/v1") // 将处理器注册到/ws/v1路径
                .setAllowedOrigins("*"); // 允许所有来源的跨域连接，生产环境中应配置为您的前端域名
    }
}
