package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.Deposit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DepositMapper {

    @Insert("INSERT INTO deposits (txid, user_id, asset_symbol, chain, amount, confirmations, status, memo, seen_at, created_at) " +
            "VALUES (#{txid}, #{userId}, #{assetSymbol}, #{chain}, #{amount}, #{confirmations}, #{status}, #{memo}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Deposit deposit);

    @Select("SELECT * FROM deposits WHERE txid = #{txid}")
    Deposit findByTxid(String txid);
}
