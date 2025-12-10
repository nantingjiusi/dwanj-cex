package com.remus.dwanjcex.disruptor.service;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.remus.dwanjcex.disruptor.event.DisruptorEvent;
import com.remus.dwanjcex.disruptor.event.EventType;
import com.remus.dwanjcex.disruptor.handler.MatchingHandler;
import com.remus.dwanjcex.disruptor.handler.PersistenceHandler;
import com.remus.dwanjcex.wallet.entity.dto.CancelOrderDto;
import com.remus.dwanjcex.wallet.entity.dto.OrderDto;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DisruptorService {

    private final MatchingHandler matchingHandler;
    private final PersistenceHandler persistenceHandler;

    private Disruptor<DisruptorEvent> disruptor;
    private RingBuffer<DisruptorEvent> ringBuffer;

    public DisruptorService(MatchingHandler matchingHandler, PersistenceHandler persistenceHandler) {
        this.matchingHandler = matchingHandler;
        this.persistenceHandler = persistenceHandler;
    }

    @PostConstruct
    public void start() {
        log.info("启动Disruptor服务...");

        EventFactory<DisruptorEvent> factory = DisruptorEvent::new;
        int bufferSize = 1024 * 1024;
        this.disruptor = new Disruptor<>(factory, bufferSize, DaemonThreadFactory.INSTANCE);

        this.disruptor.handleEventsWith(matchingHandler)
                .then(persistenceHandler);

        this.ringBuffer = this.disruptor.start();
        log.info("Disruptor服务已成功启动。");
    }

    /**
     * 发布一个 "下单" 事件到Disruptor。
     * @param orderId 数据库生成的订单ID
     * @param dto 订单数据
     */
    public void publishPlaceOrderEvent(Long orderId, OrderDto dto) {
        if (ringBuffer == null) {
            log.error("Disruptor尚未启动，无法发布下单事件。");
            return;
        }
        long sequence = ringBuffer.next();
        try {
            DisruptorEvent event = ringBuffer.get(sequence);
            event.clear();
            event.setType(EventType.PLACE_ORDER);
            event.setOrderId(orderId); // 填充独立的orderId字段
            event.setPlaceOrder(dto);
        } finally {
            log.info("[Disruptor - Publisher] 发布下单事件到RingBuffer, 序列号: {}", sequence);
            ringBuffer.publish(sequence);
        }
    }

    /**
     * 发布一个 "取消订单" 事件到Disruptor。
     * @param dto 取消订单数据
     */
    public void publishCancelOrderEvent(CancelOrderDto dto) {
        if (ringBuffer == null) {
            log.error("Disruptor尚未启动，无法发布取消事件。");
            return;
        }
        long sequence = ringBuffer.next();
        try {
            DisruptorEvent event = ringBuffer.get(sequence);
            event.clear();
            event.setType(EventType.CANCEL_ORDER);
            event.setCancelOrder(dto);
        } finally {
            log.info("[Disruptor - Publisher] 发布取消事件到RingBuffer, 序列号: {}", sequence);
            ringBuffer.publish(sequence);
        }
    }

    @PreDestroy
    public void stop() {
        log.info("关闭Disruptor服务...");
        if (disruptor != null) {
            disruptor.shutdown();
        }
        log.info("Disruptor服务已关闭。");
    }
}
