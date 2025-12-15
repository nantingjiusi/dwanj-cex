package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.WalletBalance;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface WalletBalanceMapper {

    @Insert("INSERT INTO wallets_balances (user_id, asset_symbol, chain, available, frozen, total, version, created_at, updated_at) " +
            "VALUES (#{userId}, #{assetSymbol}, #{chain}, #{available}, #{frozen}, #{total}, 0, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(WalletBalance balance);

    @Update("UPDATE wallets_balances SET " +
            "available = #{available}, " +
            "frozen = #{frozen}, " +
            "total = #{total}, " +
            "version = version + 1 " +
            "WHERE id = #{id} AND version = #{version}")
    int updateWithVersion(WalletBalance balance);

    @Select("SELECT * FROM wallets_balances WHERE user_id = #{userId} AND asset_symbol = #{assetSymbol} AND chain IS NULL")
    WalletBalance findByUserIdAndAsset(Long userId, String assetSymbol);
    
    @Select("SELECT * FROM wallets_balances WHERE user_id = #{userId}")
    List<WalletBalance> findByUserId(Long userId);
}
