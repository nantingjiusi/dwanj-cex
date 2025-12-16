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


}
