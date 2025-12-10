package com.remus.dwanjcex.disruptor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remus.dwanjcex.disruptor.handler.MatchingHandler;
import com.remus.dwanjcex.engine.OrderBook;
import com.remus.dwanjcex.wallet.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 在应用程序启动时，从Redis快照重建内存订单簿。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderBookRebuilder implements ApplicationRunner {

    private final OrderMapper orderMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MatchingHandler matchingHandler;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始从Redis快照重建订单簿...");

        List<String> symbols = orderMapper.findAllSymbols();
        if (symbols.isEmpty()) {
            log.info("没有找到任何交易对，无需重建。");
            return;
        }

        log.info("将为以下交易对重建订单簿: {}", symbols);

        for (String symbol : symbols) {
            try {
                String snapshotJson = redisTemplate.opsForValue().get("orderbook:snapshot:" + symbol);

                if (snapshotJson != null) {
                    OrderBook rebuiltOrderBook = objectMapper.readValue(snapshotJson, OrderBook.class);

                    // 1. 将重建的OrderBook注入到MatchingHandler
                    matchingHandler.getBooks().put(symbol, rebuiltOrderBook);

                    // 2. 【关键修复】立即为这个重建的订单簿生成并填充快照缓存
                    matchingHandler.rebuildCache(symbol, rebuiltOrderBook);

                    log.info("成功从Redis快照重建订单簿和快照缓存: {}", symbol);
                } else {
                    log.warn("在Redis中找不到 {} 的订单簿快照，将创建新的空订单簿。", symbol);
                }
            } catch (Exception e) {
                log.error("从Redis快照重建订单簿失败: symbol={}", symbol, e);
            }
        }

        log.info("订单簿重建完成。");
    }
}
