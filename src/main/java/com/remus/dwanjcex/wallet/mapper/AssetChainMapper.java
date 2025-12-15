package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.AssetChain;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AssetChainMapper {

    @Insert("INSERT INTO asset_chains (asset_symbol, chain, contract_address, min_deposit, min_withdraw, " +
            "withdraw_fee, confirmations, is_enabled, created_at, updated_at) " +
            "VALUES (#{assetSymbol}, #{chain}, #{contractAddress}, #{minDeposit}, #{minWithdraw}, " +
            "#{withdrawFee}, #{confirmations}, #{isEnabled}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(AssetChain assetChain);

    @Select("SELECT * FROM asset_chains WHERE asset_symbol = #{assetSymbol}")
    List<AssetChain> findByAssetSymbol(String assetSymbol);

    @Select("SELECT * FROM asset_chains WHERE asset_symbol = #{assetSymbol} AND chain = #{chain}")
    AssetChain findByAssetAndChain(String assetSymbol, String chain);
}
