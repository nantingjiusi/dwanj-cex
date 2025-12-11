package com.remus.dwanjcex.engine.stp;

import com.remus.dwanjcex.disruptor.event.DisruptorEvent;
import com.remus.dwanjcex.engine.OrderBook;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Deque;

/**
 * EXPIRE_TAKER 策略：当检测到自成交时，吃单订单（Taker）的剩余部分将立即失效。
 */
@Slf4j
@Component("expireTakerSTP")
public class ExpireTakerSTPStrategy implements STPStrategy {

    @Override
    public boolean handleSelfTrade(OrderEntity takerOrder, OrderEntity makerOrder, OrderBook orderBook, Deque<OrderEntity> makerQueue, DisruptorEvent event) {
        log.warn("STP (ExpireTaker): 检测到自成交! Taker订单 {} 将被关闭。", takerOrder.getId());
        
        // 设置标志，通知PersistenceHandler该订单的生命周期已结束
        event.setSelfTradeCancel(true);
        
        // 返回true，表示应该立即中断撮合循环
        return true;
    }
}
