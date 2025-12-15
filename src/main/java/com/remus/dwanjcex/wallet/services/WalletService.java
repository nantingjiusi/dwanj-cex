package com.remus.dwanjcex.wallet.services;

import com.remus.dwanjcex.common.KeyConstant;
import com.remus.dwanjcex.exception.BusinessException;
import com.remus.dwanjcex.wallet.entity.LedgerLog;
import com.remus.dwanjcex.wallet.entity.WalletBalance;
import com.remus.dwanjcex.wallet.mapper.LedgerLogMapper;
import com.remus.dwanjcex.wallet.mapper.WalletBalanceMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static com.remus.dwanjcex.wallet.entity.result.ResultCode.INSUFFICIENT_BALANCE;
import static com.remus.dwanjcex.wallet.entity.result.ResultCode.NO_FROZEN;

@Slf4j
@Service
public class WalletService {

    private final WalletBalanceMapper balanceMapper;
    private final LedgerLogMapper ledgerLogMapper;
    private final RedissonClient redissonClient;
    private static final int MAX_RETRIES = 5;

    public WalletService(WalletBalanceMapper balanceMapper, LedgerLogMapper ledgerLogMapper, RedissonClient redissonClient) {
        this.balanceMapper = balanceMapper;
        this.ledgerLogMapper = ledgerLogMapper;
        this.redissonClient = redissonClient;
    }

    private WalletBalance fetchOrCreate(Long userId, String assetSymbol) {
        WalletBalance b = balanceMapper.findByUserIdAndAsset(userId, assetSymbol);
        if (b != null) return b;
        
        WalletBalance newBalance = WalletBalance.builder()
                .userId(userId)
                .assetSymbol(assetSymbol)
                .chain(null)
                .available(BigDecimal.ZERO)
                .frozen(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .build();
        balanceMapper.insert(newBalance);
        return balanceMapper.findByUserIdAndAsset(userId, assetSymbol);
    }

    @Transactional
    public void deposit(Long userId, String asset, BigDecimal amount, String ref) {
        RLock lock = redissonClient.getLock(KeyConstant.LOCK_USER + userId + ":" + asset);
        try {
            lock.lock();
            for (int i = 0; i < MAX_RETRIES; i++) {
                WalletBalance balance = fetchOrCreate(userId, asset);
                BigDecimal beforeAvailable = balance.getAvailable();
                BigDecimal beforeFrozen = balance.getFrozen();

                balance.setAvailable(balance.getAvailable().add(amount));
                balance.setTotal(balance.getAvailable().add(balance.getFrozen()));

                if (balanceMapper.updateWithVersion(balance) > 0) {
                    insertLedgerLog(userId, asset, "DEPOSIT", ref, amount, beforeAvailable, beforeFrozen, balance.getAvailable(), balance.getFrozen(), "User deposit");
                    return;
                }
            }
            throw new BusinessException("Deposit failed due to high concurrency.");
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public boolean freeze(Long userId, String asset, BigDecimal amount, String ref) {
        RLock lock = redissonClient.getLock(KeyConstant.LOCK_USER + userId + ":" + asset);
        try {
            lock.lock();
            for (int i = 0; i < MAX_RETRIES; i++) {
                WalletBalance balance = fetchOrCreate(userId, asset);
                if (balance.getAvailable().compareTo(amount) < 0) {
                    return false;
                }
                BigDecimal beforeAvailable = balance.getAvailable();
                BigDecimal beforeFrozen = balance.getFrozen();

                balance.setAvailable(balance.getAvailable().subtract(amount));
                balance.setFrozen(balance.getFrozen().add(amount));
                
                if (balanceMapper.updateWithVersion(balance) > 0) {
                    log.info("用户：{},冻结金额：{}", userId, amount);
                    insertLedgerLog(userId, asset, "FREEZE", ref, amount.negate(), beforeAvailable, beforeFrozen, balance.getAvailable(), balance.getFrozen(), "Order freeze");
                    return true;
                }
            }
            throw new BusinessException("Freeze failed due to high concurrency.");
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public boolean unfreeze(Long userId, String asset, BigDecimal amount, String ref) {
        RLock lock = redissonClient.getLock(KeyConstant.LOCK_USER + userId + ":" + asset);
        try {
            lock.lock();
            for (int i = 0; i < MAX_RETRIES; i++) {
                WalletBalance balance = fetchOrCreate(userId, asset);
                if (balance.getFrozen().compareTo(amount) < 0) {
                    return false;
                }
                BigDecimal beforeAvailable = balance.getAvailable();
                BigDecimal beforeFrozen = balance.getFrozen();

                balance.setFrozen(balance.getFrozen().subtract(amount));
                balance.setAvailable(balance.getAvailable().add(amount));
                
                if (balanceMapper.updateWithVersion(balance) > 0) {
                    log.info("用户：{},解冻金额：{}", userId, amount);
                    insertLedgerLog(userId, asset, "UNFREEZE", ref, amount, beforeAvailable, beforeFrozen, balance.getAvailable(), balance.getFrozen(), "Order unfreeze");
                    return true;
                }
            }
            throw new BusinessException("Unfreeze failed due to high concurrency.");
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public void settleCredit(Long userId, String asset, BigDecimal amount, String ref) {
        RLock lock = redissonClient.getLock(KeyConstant.LOCK_USER + userId + ":" + asset);
        try {
            lock.lock();
            for (int i = 0; i < MAX_RETRIES; i++) {
                WalletBalance balance = fetchOrCreate(userId, asset);
                BigDecimal beforeAvailable = balance.getAvailable();
                BigDecimal beforeFrozen = balance.getFrozen();

                balance.setAvailable(balance.getAvailable().add(amount));
                balance.setTotal(balance.getAvailable().add(balance.getFrozen()));

                if (balanceMapper.updateWithVersion(balance) > 0) {
                    insertLedgerLog(userId, asset, "TRADE_INCOME", ref, amount, beforeAvailable, beforeFrozen, balance.getAvailable(), balance.getFrozen(), "Trade income");
                    return;
                }
            }
            throw new BusinessException("Settle credit failed due to high concurrency.");
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public void reduceFrozen(Long userId, String asset, BigDecimal amount, String reason) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return;
        
        RLock lock = redissonClient.getLock(KeyConstant.LOCK_USER + userId + ":" + asset);
        try {
            lock.lock();
            for (int i = 0; i < MAX_RETRIES; i++) {
                WalletBalance balance = balanceMapper.findByUserIdAndAsset(userId, asset);
                if (balance == null) {
                    throw new BusinessException(INSUFFICIENT_BALANCE);
                }
                if (balance.getFrozen().compareTo(amount) < 0) {
                    log.error("扣减冻结余额失败：余额不足！UserId: {}, Asset: {}, 尝试扣减: {}, 当前冻结: {}, 原因: {}",
                            userId, asset, amount, balance.getFrozen(), reason);
                    throw new BusinessException(NO_FROZEN);
                }
                BigDecimal beforeAvailable = balance.getAvailable();
                BigDecimal beforeFrozen = balance.getFrozen();

                balance.setFrozen(balance.getFrozen().subtract(amount));
                balance.setTotal(balance.getAvailable().add(balance.getFrozen()));
                
                if (balanceMapper.updateWithVersion(balance) > 0) {
                    insertLedgerLog(userId, asset, "TRADE_DEDUCT", reason, amount.negate(), beforeAvailable, beforeFrozen, balance.getAvailable(), balance.getFrozen(), "Trade deduct frozen");
                    return;
                }
            }
            throw new BusinessException("Reduce frozen failed due to high concurrency.");
        } finally {
            lock.unlock();
        }
    }

    private void insertLedgerLog(Long userId, String asset, String bizType, String bizId, BigDecimal amount,
                                 BigDecimal beforeAvailable, BigDecimal beforeFrozen,
                                 BigDecimal afterAvailable, BigDecimal afterFrozen, String remark) {
        LedgerLog logEntry = LedgerLog.builder()
                .userId(userId)
                .assetSymbol(asset)
                .bizType(bizType)
                .bizId(bizId)
                .amount(amount)
                .beforeAvailable(beforeAvailable)
                .beforeFrozen(beforeFrozen)
                .afterAvailable(afterAvailable)
                .afterFrozen(afterFrozen)
                .remark(remark)
                .build();
        ledgerLogMapper.insert(logEntry);
    }

    public WalletBalance getBalance(Long userId, String asset) {
        return fetchOrCreate(userId, asset);
    }

    public List<WalletBalance> getAllBalances(Long userId) {
        return balanceMapper.findByUserId(userId);
    }
}
