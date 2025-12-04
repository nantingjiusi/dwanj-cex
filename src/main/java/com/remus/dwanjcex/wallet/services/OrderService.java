package com.remus.dwanjcex.wallet.services;

import com.remus.dwanjcex.common.OrderStatus;
import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.common.SymbolEnum;
import com.remus.dwanjcex.engine.MatchingEngine;
import com.remus.dwanjcex.exception.BusinessException;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.dto.OrderDto;
import com.remus.dwanjcex.wallet.entity.result.ResultCode;
import com.remus.dwanjcex.wallet.mapper.OrderMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class OrderService {

    private final MatchingEngine engine;
    private final OrderMapper orderMapper;
    private final WalletService walletService;




    @Transactional
    public OrderEntity placeOrder(OrderDto dto) throws RuntimeException {
        // 构建订单实体
        OrderEntity order = OrderEntity.builder()
                .userId(dto.getUserId())
                .symbol(dto.getSymbol())
                .price(dto.getPrice())
                .amount(dto.getAmount())
                .side(dto.getSide())
                .status(OrderStatus.NEW)
                .build();

        // 插入数据库
        orderMapper.insert(order);

        // 冻结资金
        boolean freezeOk;
        if (dto.getSide() == OrderTypes.Side.BUY) {
            freezeOk = walletService.freeze(dto.getUserId(), dto.getSymbol().getQuoteCoin(), dto.getPrice().multiply(dto.getAmount()), "order:" + order.getId());
            if (!freezeOk) throw new BusinessException(ResultCode.INSUFFICIENT_QUOTE);
        } else {
            freezeOk = walletService.freeze(dto.getUserId(), dto.getSymbol().getBaseCoin(), dto.getAmount(), "order:" + order.getId());
            if (!freezeOk) throw new BusinessException(ResultCode.INSUFFICIENT_BASE);
        }

        // 下单到撮合引擎
        engine.place(order);

        return order;
    }

    @Transactional
    public void cancelOrder(Long orderId) throws RuntimeException {
        OrderEntity order = orderMapper.selectById(orderId);
        if (order == null) throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        engine.cancel(order);
    }

    /** 获取指定交易对订单簿快照 */
    public Map<String, Map<BigDecimal, List<OrderEntity>>> getOrderBook(SymbolEnum symbolEnum){
        Map<String, Map<BigDecimal, List<OrderEntity>>> orderBook = engine.getOrderBook(symbolEnum);
        return orderBook;
    }

}