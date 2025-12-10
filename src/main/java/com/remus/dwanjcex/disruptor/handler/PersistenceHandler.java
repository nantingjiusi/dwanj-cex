package com.remus.dwanjcex.disruptor.handler;

import com.lmax.disruptor.EventHandler;
import com.remus.dwanjcex.common.OrderStatus;
import com.remus.dwanjcex.disruptor.event.OrderEvent;
import com.remus.dwanjcex.disruptor.event.TradeEvent;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.SymbolEntity;
import com.remus.dwanjcex.wallet.entity.Trade;
import com.remus.dwanjcex.wallet.mapper.OrderMapper;
import com.remus.dwanjcex.wallet.mapper.TradeMapper;
import com.remus.dwanjcex.wallet.services.SymbolService;
import com.remus.dwanjcex.wallet.services.WalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 事件处理器 - 第三阶段：持久化
 * <p>
 * 消费撮合结果事件，将成交记录、订单状态更新等写入数据库。
 * </p>
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/17 11:00
 */
@Slf4j
@Component
public class PersistenceHandler implements EventHandler<OrderEvent> {

    private final WalletService walletService;
    private final OrderMapper orderMapper;
    private final TradeMapper tradeMapper;
    private final SymbolService symbolService;

    public PersistenceHandler(WalletService walletService, OrderMapper orderMapper, TradeMapper tradeMapper, SymbolService symbolService) {
        this.walletService = walletService;
        this.orderMapper = orderMapper;
        this.tradeMapper = tradeMapper;
        this.symbolService = symbolService;
    }

    @Override
    @Transactional // 将整个事件的处理放在一个事务中
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) throws Exception {
        List<TradeEvent> trades = event.getTradeEvents();
        if (trades == null || trades.isEmpty()) {
            return;
        }

        log.info("[Disruptor - Persistence] 开始持久化 {} 个成交事件...", trades.size());

        for (TradeEvent trade : trades) {
            // 1. 获取交易对信息，用于精度计算
            SymbolEntity symbol = symbolService.getSymbol(trade.getSymbol());
            if (symbol == null) {
                log.error("持久化失败：找不到交易对 {}", trade.getSymbol());
                continue;
            }
            BigDecimal tradedAmount = trade.getPrice().multiply(trade.getQuantity()).setScale(symbol.getQuoteScale(), RoundingMode.HALF_UP);

            // 2. 扣除冻结资金 (Debit)
            // 扣除买方的报价货币冻结
            walletService.reduceFrozen(trade.getBuyerUserId(), symbol.getQuoteCoin(), tradedAmount);
            // 扣除卖方的基础货币冻结
            walletService.reduceFrozen(trade.getSellerUserId(), symbol.getBaseCoin(), trade.getQuantity());

            // 3. 结算成交资产 (Credit)
            // 买方获得基础货币
            walletService.settleCredit(trade.getBuyerUserId(), symbol.getBaseCoin(), trade.getQuantity(), "trade");
            // 卖方获得报价货币
            walletService.settleCredit(trade.getSellerUserId(), symbol.getQuoteCoin(), tradedAmount, "trade");

            // 4. 插入成交记录
            Trade tradeEntity = Trade.builder()
                    .buyOrderId(trade.getBuyOrderId())
                    .sellOrderId(trade.getSellOrderId())
                    .symbol(trade.getSymbol())
                    .price(trade.getPrice())
                    .quantity(trade.getQuantity())
                    .createdAt(LocalDateTime.now())
                    .build();
            tradeMapper.insert(tradeEntity);

            // 5. 更新订单状态
            updateOrderStatus(trade.getBuyOrderId(), trade.getQuantity());
            updateOrderStatus(trade.getSellOrderId(), trade.getQuantity());
        }
        log.info("[Disruptor - Persistence] 持久化完成。");
    }

    private void updateOrderStatus(Long orderId, BigDecimal tradedQty) {
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null) {
            log.error("更新订单状态失败：找不到订单 {}", orderId);
            return;
        }

        order.addFilled(tradedQty);

        if (order.isFullyFilled()) {
            order.setStatus(OrderStatus.FILLED);
        } else {
            order.setStatus(OrderStatus.PARTIAL);
        }
        orderMapper.update(order);
    }
}
