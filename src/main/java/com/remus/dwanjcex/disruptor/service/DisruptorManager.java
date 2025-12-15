package com.remus.dwanjcex.disruptor.service;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.remus.dwanjcex.disruptor.event.DisruptorEvent;
import com.remus.dwanjcex.disruptor.event.DisruptorEventFactory;
import com.remus.dwanjcex.disruptor.handler.MatchingHandler;
import com.remus.dwanjcex.disruptor.handler.PersistenceHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DisruptorManager {

    private final ApplicationContext context;
    private final Map<String, Disruptor<DisruptorEvent>> disruptorMap = new ConcurrentHashMap<>();
    private final Map<String, RingBuffer<DisruptorEvent>> ringBufferMap = new ConcurrentHashMap<>();
    private final Map<String, MatchingHandler> matchingHandlerMap = new ConcurrentHashMap<>();

    public DisruptorManager(ApplicationContext context) {
        this.context = context;
    }

    public RingBuffer<DisruptorEvent> getRingBuffer(String symbol) {
        return ringBufferMap.computeIfAbsent(symbol, this::createDisruptorForSymbol);
    }

    public MatchingHandler getMatchingHandler(String symbol) {
        // computeIfAbsent确保了在多线程环境下，对于同一个symbol，只创建一个Disruptor引擎
        ringBufferMap.computeIfAbsent(symbol, this::createDisruptorForSymbol);
        return matchingHandlerMap.get(symbol);
    }

    private RingBuffer<DisruptorEvent> createDisruptorForSymbol(String symbol) {
        log.info("为交易对 {} 创建新的Disruptor引擎...", symbol);

        DisruptorEventFactory factory = new DisruptorEventFactory();
        int bufferSize = 1024 * 16;

        Disruptor<DisruptorEvent> disruptor = new Disruptor<>(factory, bufferSize, DaemonThreadFactory.INSTANCE);

        // 从Spring容器获取多例的Handler
        MatchingHandler matchingHandler = context.getBean(MatchingHandler.class);
        PersistenceHandler persistenceHandler = context.getBean(PersistenceHandler.class);

        // 设置处理链
        disruptor.handleEventsWith(matchingHandler)
                 .then(persistenceHandler);

        disruptor.start();
        log.info("交易对 {} 的Disruptor引擎已启动。", symbol);

        disruptorMap.put(symbol, disruptor);
        matchingHandlerMap.put(symbol, matchingHandler); // 将新创建的Handler实例存入Map
        
        return disruptor.getRingBuffer();
    }

    public void shutdownAll() {
        disruptorMap.values().forEach(Disruptor::shutdown);
        log.info("所有Disruptor引擎已关闭。");
    }
}
