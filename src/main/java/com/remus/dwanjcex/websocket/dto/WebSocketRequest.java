package com.remus.dwanjcex.websocket.dto;

import lombok.Data;

import java.util.List;

/**
 * WebSocket客户端请求的通用数据格式。
 * 例如: {"op": "subscribe", "args": ["orderbook:BTCUSDT"]}
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/18 20:20
 */
@Data
public class WebSocketRequest {

    /**
     * 操作类型，例如 "subscribe" 或 "unsubscribe"
     */
    private String op;

    /**
     * 操作参数列表，例如 ["orderbook:BTCUSDT"]
     */
    private List<String> args;
}
