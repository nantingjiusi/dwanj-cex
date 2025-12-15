package com.remus.dwanjcex.disruptor.service;

import com.lmax.disruptor.RingBuffer;
import com.remus.dwanjcex.disruptor.event.DisruptorEvent;
import com.remus.dwanjcex.disruptor.event.EventType;
import com.remus.dwanjcex.wallet.entity.dto.CancelOrderDto;
import com.remus.dwanjcex.wallet.entity.dto.OrderDto;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisruptorService {

    private final DisruptorManager disruptorManager;

    public void publishPlaceOrderEvent(Long orderId, OrderDto orderDto) {
        log.info("[Disruptor - Publisher] 准备发布下单事件: {}", orderDto.getSymbol());
        RingBuffer<DisruptorEvent> ringBuffer = disruptorManager.getRingBuffer(orderDto.getSymbol());
        long sequence = ringBuffer.next();
        try {
            DisruptorEvent event = ringBuffer.get(sequence);
            event.clear();
            event.setType(EventType.PLACE_ORDER);
            event.setOrderId(orderId);
            event.setPlaceOrder(orderDto);
        } finally {
            ringBuffer.publish(sequence);
            log.info("[Disruptor - Publisher] 成功发布下单事件到RingBuffer, 序列号: {}", sequence);
        }
    }

    public void publishCancelOrderEvent(CancelOrderDto cancelOrderDto) {
        log.info("[Disruptor - Publisher] 准备发布取消订单事件: {}", cancelOrderDto.getSymbol());
        RingBuffer<DisruptorEvent> ringBuffer = disruptorManager.getRingBuffer(cancelOrderDto.getSymbol());
        long sequence = ringBuffer.next();
        try {
            DisruptorEvent event = ringBuffer.get(sequence);
            event.clear();
            event.setType(EventType.CANCEL_ORDER);
            event.setCancelOrder(cancelOrderDto);
        } finally {
            ringBuffer.publish(sequence);
            log.info("[Disruptor - Publisher] 成功发布取消订单事件到RingBuffer, 序列号: {}", sequence);
        }
    }

    @PreDestroy
    public void stop() {
        log.info("关闭所有Disruptor引擎...");
        disruptorManager.shutdownAll();
        log.info("所有Disruptor引擎已关闭。");
    }
}
