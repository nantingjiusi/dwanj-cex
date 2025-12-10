package com.remus.dwanjcex.websocket.event;

import com.remus.dwanjcex.wallet.entity.Trade;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 交易执行事件。
 * 当一笔交易被成功持久化到数据库后，由PersistenceHandler发布。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/19 15:00
 */
@Getter
public class TradeExecutedEvent extends ApplicationEvent {

    private final Trade trade;

    public TradeExecutedEvent(Object source, Trade trade) {
        super(source);
        this.trade = trade;
    }
}
