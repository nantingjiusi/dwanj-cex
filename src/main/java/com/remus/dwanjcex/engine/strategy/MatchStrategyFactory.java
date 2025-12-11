package com.remus.dwanjcex.engine.strategy;

import com.remus.dwanjcex.common.OrderTypes;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MatchStrategyFactory {

    private final Map<OrderTypes.OrderType, MatchStrategy> strategies;

    public MatchStrategyFactory(LimitOrderMatchStrategy limitStrategy, MarketOrderMatchStrategy marketStrategy) {
        this.strategies = Map.of(
                OrderTypes.OrderType.LIMIT, limitStrategy,
                OrderTypes.OrderType.MARKET, marketStrategy
        );
    }

    public MatchStrategy getStrategy(OrderTypes.OrderType orderType) {
        MatchStrategy strategy = strategies.get(orderType);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported order type: " + orderType);
        }
        return strategy;
    }
}
