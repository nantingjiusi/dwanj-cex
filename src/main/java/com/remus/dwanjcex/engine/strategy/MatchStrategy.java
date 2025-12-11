package com.remus.dwanjcex.engine.strategy;

import com.remus.dwanjcex.disruptor.event.DisruptorEvent;
import com.remus.dwanjcex.engine.OrderBook;
import com.remus.dwanjcex.wallet.entity.OrderEntity;

public interface MatchStrategy {
    void match(OrderEntity order, OrderBook orderBook, DisruptorEvent event);
}
