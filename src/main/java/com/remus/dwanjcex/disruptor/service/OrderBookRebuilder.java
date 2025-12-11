package com.remus.dwanjcex.disruptor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remus.dwanjcex.disruptor.handler.MatchingHandler;
import com.remus.dwanjcex.engine.OrderBook;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.Trade;
import com.remus.dwanjcex.wallet.mapper.OrderMapper;
import com.remus.dwanjcex.wallet.mapper.TradeMapper;
import com.remus.dwanjcex.websocket.event.TradeExecutedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderBookRebuilder implements ApplicationRunner {

    private final OrderMapper orderMapper;
    private final TradeMapper tradeMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MatchingHandler matchingHandler;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始从Redis快照重建订单簿和最新价格...");

        List<String> symbols = orderMapper.findAllSymbols();
        if (symbols.isEmpty()) {
            log.info("没有找到任何交易对，无需重建。");
            return;
        }

        log.info("将为以下交易对重建: {}", symbols);

        for (String symbol : symbols) {
            // 1. 重建订单簿
            try {
                String snapshotJson = redisTemplate.opsForValue().get("orderbook:snapshot:" + symbol);

                if (snapshotJson != null) {
                    List<OrderEntity> activeOrders = objectMapper.readValue(snapshotJson, new TypeReference<List<OrderEntity>>() {});

                    if (!activeOrders.isEmpty()) {
                        OrderBook rebuiltOrderBook = new OrderBook(symbol);
                        for (OrderEntity order : activeOrders) {
                            rebuiltOrderBook.add(order);
                        }
                        matchingHandler.getBooks().put(symbol, rebuiltOrderBook);
                        matchingHandler.rebuildCache(symbol, rebuiltOrderBook);
                        log.info("成功从Redis快照重建订单簿: {}, 包含 {} 个活跃订单。", symbol, activeOrders.size());
                    } else {
                        log.info("Redis中 {} 的订单簿快照为空，无需重建订单。", symbol);
                    }
                } else {
                    log.warn("在Redis中找不到 {} 的订单簿快照。", symbol);
                }
            } catch (Exception e) {
                log.error("从Redis快照重建订单簿失败: symbol={}", symbol, e);
            }

            // 2. 恢复并发布最新成交价
            try {
                Trade lastTrade = tradeMapper.findLastTradeBySymbol(symbol);
                if (lastTrade != null) {
                    eventPublisher.publishEvent(new TradeExecutedEvent(this, lastTrade));
                    log.info("[OrderBookRebuilder] 成功查询到 {} 的最新成交价: {}，并发布事件。", symbol, lastTrade.getPrice());
                } else {
                    log.info("[OrderBookRebuilder] 找不到 {} 的历史成交记录，无法恢复最新价格。", symbol);
                }
            } catch (Exception e) {
                log.error("[OrderBookRebuilder] 恢复最新成交价失败: symbol={}", symbol, e);
            }
        }

        log.info("重建过程完成。");
    }
}
