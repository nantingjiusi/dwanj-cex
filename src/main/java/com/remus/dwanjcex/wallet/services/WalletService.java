package com.remus.dwanjcex.wallet.services;

import com.remus.dwanjcex.common.LedgerTxType;
import com.remus.dwanjcex.exception.BusinessException;
import com.remus.dwanjcex.wallet.entity.LedgerTx;
import com.remus.dwanjcex.wallet.entity.UserBalance;
import com.remus.dwanjcex.wallet.mapper.LedgerTxMapper;
import com.remus.dwanjcex.wallet.mapper.UserBalanceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static com.remus.dwanjcex.wallet.entity.result.ResultCode.INSUFFICIENT_BALANCE;
import static com.remus.dwanjcex.wallet.entity.result.ResultCode.NO_FROZEN;

@Service
public class WalletService {

    private final UserBalanceMapper balanceMapper;
    private final LedgerTxMapper txMapper;
    private static final int SCALE = 8;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    public WalletService(UserBalanceMapper balanceMapper, LedgerTxMapper txMapper) {
        this.balanceMapper = balanceMapper;
        this.txMapper = txMapper;
    }

    private UserBalance fetchOrCreate(Long userId, String asset) {
        UserBalance b = balanceMapper.selectByUserIdAndAsset(userId, asset);
        if (b != null) return b;
        UserBalance newB = UserBalance.builder()
                .userId(userId)
                .asset(asset)
                .available(ZERO)
                .frozen(ZERO)
                .build();
        balanceMapper.insert(newB);
        return newB;
    }

    @Transactional
    public void deposit(Long userId, String assetEnum, BigDecimal amount, String ref) {
        UserBalance b = fetchOrCreate(userId,assetEnum);
        b.setAvailable(b.getAvailable().add(amount).setScale(SCALE, BigDecimal.ROUND_HALF_UP));
        balanceMapper.update(b);
        txMapper.insert(LedgerTx.builder()
                .userId(userId)
                .asset(assetEnum)
                .amount(amount)
                .type(LedgerTxType.DEPOSIT)
                .ref(ref)
                .build());
    }

    @Transactional
    public boolean freeze(Long userId, String asset, BigDecimal amount, String ref) {
        UserBalance b = fetchOrCreate(userId, asset);
        if (b.getAvailable().compareTo(amount) < 0) {
            return false;
        }
        b.setAvailable(b.getAvailable().subtract(amount).setScale(SCALE, BigDecimal.ROUND_HALF_UP));
        b.setFrozen(b.getFrozen().add(amount).setScale(SCALE, BigDecimal.ROUND_HALF_UP));
        balanceMapper.update(b);
        txMapper.insert(LedgerTx.builder()
                .userId(userId)
                .asset(asset)
                .amount(amount)
                .type(LedgerTxType.FREEZE)
                .ref(ref)
                .build());
        return true;
    }

    @Transactional
    public boolean unfreeze(Long userId, String asset, BigDecimal amount, String ref) {
        UserBalance b = fetchOrCreate(userId, asset);
        if (b.getFrozen().compareTo(amount) < 0) {
            return false;
        }
        b.setFrozen(b.getFrozen().subtract(amount).setScale(SCALE, BigDecimal.ROUND_HALF_UP));
        b.setAvailable(b.getAvailable().add(amount).setScale(SCALE, BigDecimal.ROUND_HALF_UP));
        balanceMapper.update(b);
        txMapper.insert(LedgerTx.builder()
                .userId(userId)
                .asset(asset)
                .amount(amount)
                .type(LedgerTxType.UNFREEZE)
                .ref(ref)
                .build());
        return true;
    }

    @Transactional
    public void settleDebit(Long userId, String asset, BigDecimal amount, String ref) {
        UserBalance b = fetchOrCreate(userId, asset);
        b.setFrozen(b.getFrozen().subtract(amount).setScale(SCALE, BigDecimal.ROUND_HALF_UP));
        balanceMapper.update(b);
        txMapper.insert(LedgerTx.builder()
                .userId(userId)
                .asset(asset)
                .amount(amount)
                .type(LedgerTxType.SETTLE_DEBIT)
                .ref(ref)
                .build());
    }

    @Transactional
    public void settleCredit(Long userId, String asset, BigDecimal amount, String ref) {
        UserBalance b = fetchOrCreate(userId, asset);
        b.setAvailable(b.getAvailable().add(amount).setScale(SCALE, BigDecimal.ROUND_HALF_UP));
        balanceMapper.update(b);
        txMapper.insert(LedgerTx.builder()
                .userId(userId)
                .asset(asset)
                .amount(amount)
                .type(LedgerTxType.SETTLE_CREDIT)
                .ref(ref)
                .build());
    }

    /** 查询用户某个资产余额 */
    public UserBalance getBalance(Long userId, String asset) {
        return fetchOrCreate(userId, asset);
    }

    public void reduceFrozen(Long userId, String asset, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return; // 无需减少
        }
        // 查询用户资产
        UserBalance balance = balanceMapper.selectByUserIdAndAsset(userId, asset);
        if (balance == null) {
            throw new BusinessException(INSUFFICIENT_BALANCE);
        }

        BigDecimal frozen = balance.getFrozen();
        if (frozen.compareTo(amount) < 0) {
            throw new BusinessException(NO_FROZEN);
        }

        // 减少冻结资金
        balance.setFrozen(frozen.subtract(amount));

        // 更新数据库
        balanceMapper.update(balance);
    }
}
