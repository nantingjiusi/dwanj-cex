package com.remus.dwanjcex.common;

public enum LedgerTxType {
    DEPOSIT,       // 存款
    FREEZE,        // 冻结
    UNFREEZE,      // 解冻
    SETTLE_DEBIT,  // 扣除冻结资产（结算卖单）
    SETTLE_CREDIT  // 增加可用资产（结算买单）
}