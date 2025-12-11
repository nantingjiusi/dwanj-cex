package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.SymbolEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 交易对Mapper
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/16 10:05
 */
@Mapper
public interface SymbolMapper {

    /**
     * 查询所有已上线的交易对
     *
     * @return 交易对列表
     */
    @Select("""
            SELECT
                id,
                symbol,
                base_coin,
                quote_coin,
                base_scale,
                quote_scale,
                status
            FROM
                t_symbol
            WHERE
                status = 1
            """)
//    @Results({
//            @Result(property = "baseCoin", column = "base_coin"),
//            @Result(property = "quoteCoin", column = "quote_coin"),
//            @Result(property = "baseScale", column = "base_scale"),
//            @Result(property = "quoteScale", column = "quote_scale")
//    })
    List<SymbolEntity> findAll();
}
