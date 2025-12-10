package com.remus.dwanjcex.wallet.services;

import com.remus.dwanjcex.common.OrderStatus;
import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.disruptor.event.OrderCreatedEvent;
import com.remus.dwanjcex.disruptor.handler.MatchingHandler;
import com.remus.dwanjcex.exception.BusinessException;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.SymbolEntity;
import com.remus.dwanjcex.wallet.entity.dto.OrderBookLevel;
import com.remus.dwanjcex.wallet.entity.dto.OrderDto;
import com.remus.dwanjcex.wallet.entity.result.ResultCode;
import com.remus.dwanjcex.wallet.mapper.OrderMapper;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final WalletService walletService;
    private final SymbolService symbolService;
    private final MatchingHandler matchingHandler;
    private final ApplicationEventPublisher eventPublisher; // 注入Spring事件发布器

    @Transactional
    public OrderEntity placeOrder(OrderDto dto) throws RuntimeException {
        // 1. 校验交易对是否存在
        SymbolEntity symbol = symbolService.getSymbol(dto.getSymbol());
        if (symbol == null) {
            throw new BusinessException(ResultCode.SYMBOL_NOT_SUPPORTED);
        }

        // 2. 构建订单实体并初步入库，获取订单ID
        OrderEntity order = OrderEntity.builder()
                .userId(dto.getUserId())
                .symbol(dto.getSymbol())
                .price(dto.getPrice())
                .amount(dto.getAmount())
                .side(dto.getSide())
                .status(OrderStatus.NEW)
                .build();
        orderMapper.insert(order);

        // 3. 冻结资金
        boolean freezeOk;
        if (dto.getSide() == OrderTypes.Side.BUY) {
            BigDecimal amountToFreeze = dto.getPrice().multiply(dto.getAmount()).setScale(symbol.getQuoteScale(), RoundingMode.HALF_UP);
            freezeOk = walletService.freeze(dto.getUserId(), symbol.getQuoteCoin(), amountToFreeze, "order:" + order.getId());
            if (!freezeOk) throw new BusinessException(ResultCode.INSUFFICIENT_QUOTE);
        } else {
            freezeOk = walletService.freeze(dto.getUserId(), symbol.getBaseCoin(), dto.getAmount(), "order:" + order.getId());
            if (!freezeOk) throw new BusinessException(ResultCode.INSUFFICIENT_BASE);
        }

        // 4. 发布一个Spring事件，通知订单已创建
        // 这个事件将在当前事务成功提交后，由@TransactionalEventListener进行异步处理
        eventPublisher.publishEvent(new OrderCreatedEvent(this, order.getId(), dto));

        // 5. 立即返回，不等待撮合结果
        return order;
    }

    @Transactional
    public void cancelOrder(Long orderId) throws RuntimeException {
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        // TODO: 实现订单取消逻辑。也需要通过Disruptor发布一个"取消事件"。
        throw new UnsupportedOperationException("订单取消功能尚未接入Disruptor。");
    }

    /**
     * 获取指定交易对订单簿快照。
     * 此方法从MatchingHandler的缓存中无锁读取，性能极高。
     */
    public Map<String, List<OrderBookLevel>> getOrderBook(String symbol){
        return matchingHandler.getOrderBookSnapshot(symbol);
    }

}
