package com.remus.dwanjcex.wallet.services;

import com.remus.dwanjcex.common.OrderStatus;
import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.disruptor.event.OrderCancelEvent;
import com.remus.dwanjcex.disruptor.event.OrderCreatedEvent;
import com.remus.dwanjcex.disruptor.handler.MatchingHandler;
import com.remus.dwanjcex.exception.BusinessException;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.SymbolEntity;
import com.remus.dwanjcex.wallet.entity.dto.CancelOrderDto;
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
import java.util.Objects;

@Service
@AllArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final WalletService walletService;
    private final SymbolService symbolService;
    private final MatchingHandler matchingHandler;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderEntity placeOrder(OrderDto dto) throws RuntimeException {
        SymbolEntity symbol = symbolService.getSymbol(dto.getSymbol());
        if (symbol == null) {
            throw new BusinessException(ResultCode.SYMBOL_NOT_SUPPORTED);
        }

        OrderEntity order = OrderEntity.builder()
                .userId(dto.getUserId())
                .symbol(dto.getSymbol())
                .price(dto.getPrice())
                .amount(dto.getAmount())
                .side(dto.getSide())
                .status(OrderStatus.NEW)
                .build();
        orderMapper.insert(order);

        boolean freezeOk;
        if (dto.getSide() == OrderTypes.Side.BUY) {
            BigDecimal amountToFreeze = dto.getPrice().multiply(dto.getAmount()).setScale(symbol.getQuoteScale(), RoundingMode.HALF_UP);
            freezeOk = walletService.freeze(dto.getUserId(), symbol.getQuoteCoin(), amountToFreeze, "order:" + order.getId());
            if (!freezeOk) throw new BusinessException(ResultCode.INSUFFICIENT_QUOTE);
        } else {
            freezeOk = walletService.freeze(dto.getUserId(), symbol.getBaseCoin(), dto.getAmount(), "order:" + order.getId());
            if (!freezeOk) throw new BusinessException(ResultCode.INSUFFICIENT_BASE);
        }

        // 注意：这里的dto是原始请求，order.getId()是新生成的ID
        eventPublisher.publishEvent(new OrderCreatedEvent(this, order.getId(), dto));
        return order;
    }
    @Transactional
    public void cancelOrder( Long userId,Long orderId) throws RuntimeException {
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_BELONG_TO_USER);
        }
        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PARTIAL) {
            throw new BusinessException(ResultCode.ORDER_CANNOT_BE_CANCELED);
        }
        CancelOrderDto cancelDto = new CancelOrderDto(orderId, userId, order.getSymbol(), order.getSide());
        eventPublisher.publishEvent(new OrderCancelEvent(this, cancelDto));
    }

    public Map<String, List<OrderBookLevel>> getOrderBook(String symbol){
        return matchingHandler.getOrderBookSnapshot(symbol);
    }
}
