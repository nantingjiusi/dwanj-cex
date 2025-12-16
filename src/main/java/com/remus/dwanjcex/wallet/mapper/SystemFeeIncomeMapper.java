package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.SystemFeeIncome;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

import java.util.List;

@Mapper
public interface SystemFeeIncomeMapper {

    @Insert("<script>" +
            "INSERT INTO system_fee_income (asset_symbol, amount, trade_id, user_id, fee_type, created_at) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.assetSymbol}, #{item.amount}, #{item.tradeId}, #{item.userId}, #{item.feeType}, NOW())" +
            "</foreach>" +
            "</script>")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insertBatch(List<SystemFeeIncome> feeIncomes);
}
