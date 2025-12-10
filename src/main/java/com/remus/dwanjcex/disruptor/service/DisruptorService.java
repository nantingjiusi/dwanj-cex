package com.remus.dwanjcex.disruptor.service;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.remus.dwanjcex.disruptor.event.OrderEvent;
import com.remus.dwanjcex.disruptor.handler.MatchingHandler;
import com.remus.dwanjcex.disruptor.handler.PersistenceHandler;
import com.remus.dwanjcex.wallet.entity.dto.OrderDto;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DisruptorService {

    // 移除了 JournalingHandler 的引用
    private final MatchingHandler matchingHandler;
    private final PersistenceHandler persistenceHandler;

    private Disruptor<OrderEvent> disruptor;
    private RingBuffer<OrderEvent> ringBuffer;

    public DisruptorService(MatchingHandler matchingHandler, PersistenceHandler persistenceHandler) {
        this.matchingHandler = matchingHandler;
        this.persistenceHandler = persistenceHandler;
    }

    @PostConstruct
    public void start() {
        log.info("启动Disruptor服务...");

        EventFactory<OrderEvent> factory = OrderEvent::new;
        int bufferSize = 1024 * 1024;
        this.disruptor = new Disruptor<>(factory, bufferSize, DaemonThreadFactory.INSTANCE);

        // 调整处理链，直接从 MatchingHandler 开始
        this.disruptor.handleEventsWith(matchingHandler)
                .then(persistenceHandler);

        this.ringBuffer = this.disruptor.start();
        log.info("Disruptor服务已成功启动。");
    }

    /**
     * 将从Kafka消费的订单发布到Disruptor环形缓冲区。
     *
     * @param orderId 订单ID
     * @param dto     订单数据传输对象
     */
    public void publishToRingBuffer(Long orderId, OrderDto dto) {
        if (ringBuffer == null) {
            log.error("Disruptor尚未启动，无法发布事件。");
            return;
        }

        long sequence = ringBuffer.next();
        try {
            OrderEvent event = ringBuffer.get(sequence);
            event.setOrderId(orderId);
            event.setSymbol(dto.getSymbol());
            event.setPrice(dto.getPrice());
            event.setAmount(dto.getAmount());
            event.setSide(dto.getSide());
            event.setUserId(dto.getUserId());
        } finally {
            log.info("[Disruptor - Publisher] 发布事件到RingBuffer, 序列号: {}", sequence);
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
