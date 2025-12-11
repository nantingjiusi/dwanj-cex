package com.remus.dwanjcex.wallet.services;

import com.remus.dwanjcex.common.LedgerTxType;
import com.remus.dwanjcex.exception.BusinessException;
import com.remus.dwanjcex.wallet.entity.LedgerTx;
import com.remus.dwanjcex.wallet.entity.UserBalance;
import com.remus.dwanjcex.wallet.mapper.LedgerTxMapper;
import com.remus.dwanjcex.wallet.mapper.UserBalanceMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static com.remus.dwanjcex.wallet.entity.result.ResultCode.INSUFFICIENT_BALANCE;
import static com.remus.dwanjcex.wallet.entity.result.ResultCode.NO_FROZEN;

@Slf4j
@Service
public class WalletService {

    private final UserBalanceMapper balanceMapper;
    private final LedgerTxMapper txMapper;
    private final RedissonClient redissonClient; // 注入Redisson客户端
    private static final int SCALE = 8;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    public WalletService(UserBalanceMapper balanceMapper, LedgerTxMapper txMapper, RedissonClient redissonClient) {
        this.balanceMapper = balanceMapper;
        this.txMapper = txMapper;
        this.redissonClient = redissonClient;
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
    public void deposit(Long userId, String asset, BigDecimal amount, String ref) {
        RLock lock = redissonClient.getLock("lock:user:" + userId);
        try {
            lock.lock();
            UserBalance b = fetchOrCreate(userId, asset);
            b.setAvailable(b.getAvailable().add(amount).setScale(SCALE, RoundingMode.HALF_UP));
            balanceMapper.update(b);
            txMapper.insert(LedgerTx.builder().userId(userId).asset(asset).amount(amount).type(LedgerTxType.DEPOSIT).ref(ref).build());
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public boolean freeze(Long userId, String asset, BigDecimal amount, String ref) {
        RLock lock = redissonClient.getLock("lock:user:" + userId);
        try {
            lock.lock();
            UserBalance b = fetchOrCreate(userId, asset);
            if (b.getAvailable().compareTo(amount) < 0) {
                return false;
            }
            b.setAvailable(b.getAvailable().subtract(amount).setScale(SCALE, RoundingMode.HALF_UP));
            b.setFrozen(b.getFrozen().add(amount).setScale(SCALE, RoundingMode.HALF_UP));
            balanceMapper.update(b);
            txMapper.insert(LedgerTx.builder().userId(userId).asset(asset).amount(amount).type(LedgerTxType.FREEZE).ref(ref).build());
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public boolean unfreeze(Long userId, String asset, BigDecimal amount, String ref) {
        RLock lock = redissonClient.getLock("lock:user:" + userId);
        try {
            lock.lock();
            UserBalance b = fetchOrCreate(userId, asset);
            if (b.getFrozen().compareTo(amount) < 0) {
                return false;
            }
            b.setFrozen(b.getFrozen().subtract(amount).setScale(SCALE, RoundingMode.HALF_UP));
            b.setAvailable(b.getAvailable().add(amount).setScale(SCALE, RoundingMode.HALF_UP));
            balanceMapper.update(b);
            txMapper.insert(LedgerTx.builder().userId(userId).asset(asset).amount(amount).type(LedgerTxType.UNFREEZE).ref(ref).build());
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public void settleCredit(Long userId, String asset, BigDecimal amount, String ref) {
        RLock lock = redissonClient.getLock("lock:user:" + userId);
        try {
            lock.lock();
            UserBalance b = fetchOrCreate(userId, asset);
            b.setAvailable(b.getAvailable().add(amount).setScale(SCALE, RoundingMode.HALF_UP));
            balanceMapper.update(b);
            txMapper.insert(LedgerTx.builder().userId(userId).asset(asset).amount(amount).type(LedgerTxType.SETTLE_CREDIT).ref(ref).build());
        } finally {
            lock.unlock();
        }
    }

    public void reduceFrozen(Long userId, String asset, BigDecimal amount, String reason) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return;
        
        RLock lock = redissonClient.getLock("lock:user:" + userId);
        try {
            lock.lock();
            UserBalance balance = balanceMapper.selectByUserIdAndAsset(userId, asset);
            if (balance == null) {
                throw new BusinessException(INSUFFICIENT_BALANCE);
            }

            BigDecimal frozen = balance.getFrozen();
            if (frozen.compareTo(amount) < 0) {
                log.error("扣减冻结余额失败：余额不足！UserId: {}, Asset: {}, 尝试扣减: {}, 当前冻结: {}, 原因: {}",
                        userId, asset, amount, frozen, reason);
                throw new BusinessException(NO_FROZEN);
            }

            balance.setFrozen(frozen.subtract(amount));
            balanceMapper.update(balance);
        } finally {
            lock.unlock();
        }
    }

    public UserBalance getBalance(Long userId, String asset) {
        return fetchOrCreate(userId, asset);
    }

    public List<UserBalance> getAllBalances(Long userId) {
        return balanceMapper.selectByUserId(userId);
    }
}
