package com.remus.dwanjcex.websocket.event;

import com.remus.dwanjcex.wallet.entity.dto.OrderBookLevel;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;
import java.util.Map;

/**
 * 订单簿更新的内部Spring事件。
 * 当MatchingHandler更新了某个交易对的订单簿时发布。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/18 20:25
 */
@Getter
public class OrderBookUpdateEvent extends ApplicationEvent {

    private final String symbol;
    private final Map<String, List<OrderBookLevel>> orderBookData;

    /**
     * @param source        事件源
     * @param symbol        交易对名称
     * @param orderBookData 最新的订单簿快照数据
     */
    public OrderBookUpdateEvent(Object source, String symbol, Map<String, List<OrderBookLevel>> orderBookData) {
        super(source);
        this.symbol = symbol;
        this.orderBookData = orderBookData;
    }
}
