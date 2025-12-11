package com.remus.dwanjcex.wallet.services;

import com.remus.dwanjcex.common.KeyConstant;
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

        OrderEntity.OrderEntityBuilder builder = OrderEntity.builder()
                .userId(dto.getUserId())
                .symbol(dto.getSymbol())
                .type(dto.getType())
                .side(dto.getSide())
                .status(OrderStatus.NEW);

        if (dto.getType() == OrderTypes.OrderType.LIMIT) {
            builder.price(dto.getPrice()).amount(dto.getAmount());
        } else { // MARKET
            builder.price(BigDecimal.ZERO); // 市价单价格存为0
            if (dto.getSide() == OrderTypes.Side.BUY) {
                builder.quoteAmount(dto.getQuoteAmount());
            } else {
                builder.amount(dto.getAmount());
            }
        }

        OrderEntity order = builder.build();
        orderMapper.insert(order);

        freezeFunds(order, symbol);

        eventPublisher.publishEvent(new OrderCreatedEvent(this, order.getId(), dto));
        return order;
    }

    private void freezeFunds(OrderEntity order, SymbolEntity symbol) {
        boolean freezeOk;
        if (order.getSide() == OrderTypes.Side.BUY) {
            BigDecimal amountToFreeze;
            if (order.getType() == OrderTypes.OrderType.LIMIT) {
                amountToFreeze = order.getPrice().multiply(order.getAmount()).setScale(symbol.getQuoteScale(), RoundingMode.HALF_UP);
            } else { // MARKET BUY
                amountToFreeze = order.getQuoteAmount();
            }
            freezeOk = walletService.freeze(order.getUserId(), symbol.getQuoteCoin(), amountToFreeze, KeyConstant.ORDER_FREEZE + order.getId());
            if (!freezeOk) throw new BusinessException(ResultCode.INSUFFICIENT_QUOTE);
        } else { // SELL
            freezeOk = walletService.freeze(order.getUserId(), symbol.getBaseCoin(), order.getAmount(), KeyConstant.ORDER_FREEZE + order.getId());
            if (!freezeOk) throw new BusinessException(ResultCode.INSUFFICIENT_BASE);
        }
    }

    @Transactional
    public void cancelOrder(Long userId, Long orderId) throws RuntimeException {
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new BusinessException(ResultCode.ORDER_NOT_BELONG_TO_USER);
        }
        // 只有NEW或PARTIAL状态的订单才能被取消
        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PARTIAL) {
            throw new BusinessException(ResultCode.ORDER_CANNOT_BE_CANCELED);
        }

        // 创建取消订单的DTO，包含所有必要信息
        CancelOrderDto cancelDto = new CancelOrderDto(orderId, userId, order.getSymbol(), order.getSide());

        // 发布取消订单的Spring事件，由Disruptor异步处理
        eventPublisher.publishEvent(new OrderCancelEvent(this, cancelDto));
    }

    public Map<String, List<OrderBookLevel>> getOrderBook(String symbol){
        return matchingHandler.getOrderBookSnapshot(symbol);
    }

    public List<OrderEntity> getMyOrders(Long userId) {
        return orderMapper.selectByUserId(userId);
    }
}
