package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.Asset;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AssetMapper {

    @Insert("INSERT INTO assets (symbol, display_name, precision_digits, min_size, max_size, " +
            "is_tradable, is_deposit_enabled, is_withdraw_enabled, is_enabled, created_at, updated_at) " +
            "VALUES (#{symbol}, #{displayName}, #{precisionDigits}, #{minSize}, #{maxSize}, " +
            "#{isTradable}, #{isDepositEnabled}, #{isWithdrawEnabled}, #{isEnabled}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Asset asset);

    @Select("SELECT * FROM assets WHERE symbol = #{symbol}")
    Asset findBySymbol(String symbol);

    @Select("SELECT * FROM assets")
    List<Asset> findAll();
}
