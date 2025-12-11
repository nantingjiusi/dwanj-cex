package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.SymbolEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SymbolMapper {

    @Select("SELECT * FROM t_symbol")
    List<SymbolEntity> findAll();

    /**
     * 【新增】根据交易对名称查询
     */
    @Select("SELECT * FROM t_symbol WHERE symbol = #{symbol}")
    SymbolEntity findBySymbol(String symbol);

    /**
     * 【新增】插入新的交易对
     */
    @Insert("INSERT INTO t_symbol (symbol, base_coin, quote_coin, base_scale, quote_scale, min_order_value, created_at, updated_at) " +
            "VALUES (#{symbol}, #{baseCoin}, #{quoteCoin}, #{baseScale}, #{quoteScale}, #{minOrderValue}, NOW(), NOW())")
    void insert(SymbolEntity symbol);
}
