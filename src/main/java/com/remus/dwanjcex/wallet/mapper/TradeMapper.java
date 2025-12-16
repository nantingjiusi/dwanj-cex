package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.Trade;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TradeMapper {

    @Insert("<script>" +
            "INSERT INTO trades(market_symbol, price, quantity, taker_order_id, maker_order_id, taker_user_id, maker_user_id, fee, created_at) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.marketSymbol}, #{item.price}, #{item.quantity}, #{item.takerOrderId}, #{item.makerOrderId}, #{item.takerUserId}, #{item.makerUserId}, #{item.fee}, NOW())" +
            "</foreach>" +
            "</script>")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertBatch(List<Trade> trades);

    @Select("SELECT * FROM trades WHERE taker_user_id = #{userId} OR maker_user_id = #{userId} ORDER BY id DESC")
    List<Trade> findByUserId(Long userId);

    @Select("SELECT * FROM trades WHERE market_symbol = #{symbol} ORDER BY id DESC LIMIT 1")
    Trade findLastTradeBySymbol(String symbol);
}
