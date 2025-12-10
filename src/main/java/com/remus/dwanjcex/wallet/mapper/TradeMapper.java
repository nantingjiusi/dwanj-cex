package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.Trade;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TradeMapper {
    @Insert("   INSERT INTO trade(buy_order_id, sell_order_id, symbol,price, quantity, created_at)\n" +
            "        VALUES(#{buyOrderId}, #{sellOrderId}, #{symbol}, #{price}, #{quantity}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Trade trade);
//    void insertBatch(List<Trade> trades);
//    List<Trade> selectBySymbol(String symbol);
    @Select("select t1.id,t1.symbol,t1.price,t1.quantity,t1.created_at,t2.user_id,t2.side from trade t1 join orders t2 where (t1.buy_order_id = t2.id or t1.sell_order_id = t2.id) and t2.user_id = #{userId}")
    List<Trade> selectByUserId(Long userId);
}
