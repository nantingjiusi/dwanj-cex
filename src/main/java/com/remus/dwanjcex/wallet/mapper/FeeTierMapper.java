package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.FeeTier;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FeeTierMapper {

    @Insert("INSERT INTO fee_tiers (tier_name, maker_rate, taker_rate, min_volume) " +
            "VALUES (#{tierName}, #{makerRate}, #{takerRate}, #{minVolume})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(FeeTier feeTier);

    @Select("SELECT * FROM fee_tiers ORDER BY min_volume DESC")
    List<FeeTier> findAll();
}
