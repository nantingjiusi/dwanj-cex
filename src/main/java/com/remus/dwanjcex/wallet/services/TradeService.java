package com.remus.dwanjcex.wallet.services;

import com.remus.dwanjcex.wallet.entity.Trade;
import com.remus.dwanjcex.wallet.mapper.TradeMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TradeService {
    private final TradeMapper tradeMapper;

    public TradeService(TradeMapper tradeMapper) {
        this.tradeMapper = tradeMapper;
    }

    /**
     * 查询某用户作为买方或卖方的成交记录
     *
     * @param userId 用户ID
     * @return 成交记录列表
     */
    public List<Trade> getTradesByUser(Long userId) {
        return tradeMapper.selectByUserId(userId);
    }
}
