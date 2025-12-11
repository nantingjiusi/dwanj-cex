package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.Asset;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AssetMapper {

    @Insert("INSERT INTO assets (symbol, name, scale, is_enabled,created_at,updated_at) " +
            "VALUES (#{symbol}, #{name}, #{scale}, #{isEnabled},#{createdAt},#{updatedAt})")
    void insert(Asset asset);

    @Select("SELECT * FROM assets WHERE symbol = #{symbol}")
    Asset findBySymbol(String symbol);

    @Select("SELECT * FROM assets")
    List<Asset> findAll();
}
