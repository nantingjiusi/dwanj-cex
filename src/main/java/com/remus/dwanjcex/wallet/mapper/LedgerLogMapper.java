package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.LedgerLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface LedgerLogMapper {

    @Insert("INSERT INTO ledger_log (user_id, asset_symbol, chain, biz_type, biz_id, amount, " +
            "before_available, before_frozen, after_available, after_frozen, remark, created_at) " +
            "VALUES (#{userId}, #{assetSymbol}, #{chain}, #{bizType}, #{bizId}, #{amount}, " +
            "#{beforeAvailable}, #{beforeFrozen}, #{afterAvailable}, #{afterFrozen}, #{remark}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(LedgerLog log);
}
