package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.Trade;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TradeMapper {
    @Insert("INSERT INTO trade(buy_order_id, sell_order_id, symbol, price, quantity, created_at) " +
            "VALUES(#{buyOrderId}, #{sellOrderId}, #{symbol}, #{price}, #{quantity}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Trade trade);

    /**
     * 批量插入成交记录
     */
    @Insert("<script>" +
            "INSERT INTO trade(buy_order_id, sell_order_id, symbol, price, quantity, created_at) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.buyOrderId}, #{item.sellOrderId}, #{item.symbol}, #{item.price}, #{item.quantity}, NOW())" +
            "</foreach>" +
            "</script>")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertBatch(List<Trade> trades);

    @Select("SELECT * FROM trade WHERE user_id = #{userId}")
    List<Trade> selectByUserId(Long userId);

    @Select("SELECT * FROM trade WHERE symbol = #{symbol} ORDER BY id DESC LIMIT 1")
    Trade findLastTradeBySymbol(String symbol);
}
