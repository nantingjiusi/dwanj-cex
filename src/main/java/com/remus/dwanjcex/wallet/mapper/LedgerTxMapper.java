package com.remus.dwanjcex.wallet.mapper;

import com.remus.dwanjcex.wallet.entity.LedgerTx;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

import java.util.List;

@Mapper
public interface LedgerTxMapper {

    @Insert("  INSERT INTO ledger_tx(user_id, asset, amount, type, ref, created_at)\n" +
            "        VALUES(#{userId}, #{asset}, #{amount}, #{type}, #{ref}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(LedgerTx tx);
    void insertBatch(List<LedgerTx> txList);
    List<LedgerTx> selectByUserId(Long userId);
}
