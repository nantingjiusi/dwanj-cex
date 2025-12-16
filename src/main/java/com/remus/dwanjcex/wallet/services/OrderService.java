package com.remus.dwanjcex.wallet.services;

import com.remus.dwanjcex.common.KeyConstant;
import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.disruptor.event.OrderCancelEvent;
import com.remus.dwanjcex.disruptor.event.OrderCreatedEvent;
import com.remus.dwanjcex.disruptor.handler.MatchingHandler;
import com.remus.dwanjcex.exception.BusinessException;
import com.remus.dwanjcex.wallet.entity.Market;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.dto.CancelOrderDto;
import com.remus.dwanjcex.wallet.entity.dto.OrderBookLevel;
import com.remus.dwanjcex.wallet.entity.dto.OrderDto;
import com.remus.dwanjcex.wallet.entity.result.ResultCode;
import com.remus.dwanjcex.wallet.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final WalletService walletService;
    private final MarketService marketService;
    private final MatchingHandler matchingHandler;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderEntity placeOrder(OrderDto dto) throws RuntimeException {
        Market market = marketService.getMarket(dto.getSymbol());
        if (market == null) {
            throw new BusinessException(ResultCode.SYMBOL_NOT_SUPPORTED);
        }

        OrderEntity.OrderEntityBuilder builder = OrderEntity.builder()
                .userId(dto.getUserId())
                .marketSymbol(dto.getSymbol())
                .type(dto.getType())
                .side(dto.getSide());

        if (dto.getType() == OrderTypes.OrderType.LIMIT) {
            builder.price(dto.getPrice()).quantity(dto.getAmount());
        } else { // MARKET
            builder.price(BigDecimal.ZERO);
            if (dto.getSide() == OrderTypes.Side.BUY) {
                builder.quoteAmount(dto.getQuoteAmount());
            } else {
                builder.quantity(dto.getAmount());
            }
        }

        OrderEntity order = builder.build();
        order.init(); // 【关键修改】初始化状态
        orderMapper.insert(order);

        freezeFunds(order, market);

        eventPublisher.publishEvent(new OrderCreatedEvent(this, order.getId(), dto));
        return order;
    }

    private void freezeFunds(OrderEntity order, Market market) {
        boolean freezeOk;
        if (order.getSide() == OrderTypes.Side.BUY) {
            BigDecimal amountToFreeze;
            if (order.getType() == OrderTypes.OrderType.LIMIT) {
                amountToFreeze = order.getPrice().multiply(order.getQuantity()).setScale(market.getPricePrecision(), RoundingMode.HALF_UP);
            } else { // MARKET BUY
                amountToFreeze = order.getQuoteAmount();
            }
            freezeOk = walletService.freeze(order.getUserId(), market.getQuoteAsset(), amountToFreeze, KeyConstant.ORDER_FREEZE + order.getId());
            if (!freezeOk) throw new BusinessException(ResultCode.INSUFFICIENT_QUOTE);
        } else { // SELL
            freezeOk = walletService.freeze(order.getUserId(), market.getBaseAsset(), order.getQuantity(), KeyConstant.ORDER_FREEZE + order.getId());
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
        
        // 状态模式下，不再需要在这里检查状态
        // if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.PARTIAL) { ... }

        CancelOrderDto cancelDto = new CancelOrderDto(orderId, userId, order.getMarketSymbol(), order.getSide());
        eventPublisher.publishEvent(new OrderCancelEvent(this, cancelDto));
    }

    public Map<String, List<OrderBookLevel>> getOrderBook(String symbol){
        return matchingHandler.getOrderBookSnapshot(symbol);
    }

    public List<OrderEntity> getMyOrders(Long userId) {
        return orderMapper.selectByUserId(userId);
    }
}
