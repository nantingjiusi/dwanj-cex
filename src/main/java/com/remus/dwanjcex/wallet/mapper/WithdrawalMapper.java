package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.Withdrawal;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WithdrawalMapper {

    @Insert("INSERT INTO withdrawals (withdraw_id, user_id, asset_symbol, chain, to_address, amount, fee, status, txid, created_at, updated_at) " +
            "VALUES (#{withdrawId}, #{userId}, #{assetSymbol}, #{chain}, #{toAddress}, #{amount}, #{fee}, #{status}, #{txid}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Withdrawal withdrawal);

    @Select("SELECT * FROM withdrawals WHERE id = #{id}")
    Withdrawal findById(Long id);

    @Select("SELECT * FROM withdrawals WHERE withdraw_id = #{withdrawId}")
    Withdrawal findByWithdrawId(String withdrawId);
}
