package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.Market;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MarketMapper {

    @Insert("INSERT INTO markets (symbol, base_asset, quote_asset, price_precision, quantity_precision, " +
            "min_order_size, min_order_value, maker_fee_rate, taker_fee_rate, is_enabled, created_at, updated_at) " +
            "VALUES (#{symbol}, #{baseAsset}, #{quoteAsset}, #{pricePrecision}, #{quantityPrecision}, " +
            "#{minOrderSize}, #{minOrderValue}, #{makerFeeRate}, #{takerFeeRate}, #{isEnabled}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Market market);

    @Select("SELECT * FROM markets WHERE symbol = #{symbol}")
    Market findBySymbol(String symbol);

    @Select("SELECT * FROM markets")
    List<Market> findAll();
    
    @Select("SELECT symbol FROM markets WHERE is_enabled = 1")
    List<String> findAllSymbols();
}
