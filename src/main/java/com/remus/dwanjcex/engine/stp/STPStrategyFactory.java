package com.remus.dwanjcex.engine.stp;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class STPStrategyFactory {

    private final ApplicationContext context;

    @Value("${cex.stp-strategy:expireTakerSTP}") // 默认为ExpireTaker策略
    private String activeStrategyName;

    public STPStrategy getActiveStrategy() {
        return context.getBean(activeStrategyName, STPStrategy.class);
    }
}
