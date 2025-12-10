package com.remus.dwanjcex.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket服务器向客户端推送消息的通用格式。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/18 20:30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketPushMessage<T> {

    /**
     * 推送的主题，例如 "orderbook:BTCUSDT"
     */
    private String topic;

    /**
     * 推送的具体数据
     */
    private T data;
}
