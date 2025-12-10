package com.remus.dwanjcex.websocket;

import com.remus.dwanjcex.config.jwt.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket握手拦截器，用于在连接建立前验证JWT。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/19 10:30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtils jwtUtils;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // 客户端应通过URL参数传递Token，例如: ws://localhost:8080/ws/v1?token=xxx
        String token = request.getURI().getQuery();
        if (StringUtils.hasText(token) && token.startsWith("token=")) {
            token = token.substring(6);
        }

        if (StringUtils.hasText(token) && jwtUtils.validateToken(token)) {
            try {
                Long userId = jwtUtils.getUserIdFromToken(token);
                // 将验证通过的userId存入WebSocket session的attributes中
                attributes.put("userId", userId);
                log.info("WebSocket握手成功，用户ID: {}", userId);
                return true; // 允许握手
            } catch (Exception e) {
                log.warn("WebSocket握手失败：无效的Token", e);
                return false; // 拒绝握手
            }
        }

        log.warn("WebSocket握手失败：缺少或无效的Token");
        return false; // 拒绝握手
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
        // do nothing
    }
}
