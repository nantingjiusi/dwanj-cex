package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.DepositAddress;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DepositAddressMapper {

    @Insert("INSERT INTO deposit_addresses (user_id, asset_symbol, chain, address, tag, created_at) " +
            "VALUES (#{userId}, #{assetSymbol}, #{chain}, #{address}, #{tag}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(DepositAddress depositAddress);

    @Select("SELECT * FROM deposit_addresses WHERE user_id = #{userId} AND asset_symbol = #{assetSymbol}")
    List<DepositAddress> findByUserIdAndAsset(Long userId, String assetSymbol);
}
