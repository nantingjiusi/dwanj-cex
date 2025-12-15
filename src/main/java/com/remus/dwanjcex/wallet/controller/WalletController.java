package com.remus.dwanjcex.wallet.controller;

import com.remus.dwanjcex.config.jwt.UserContextHolder;
import com.remus.dwanjcex.wallet.entity.WalletBalance;
import com.remus.dwanjcex.wallet.entity.result.ResponseResult;
import com.remus.dwanjcex.wallet.entity.result.ResultCode;
import com.remus.dwanjcex.wallet.services.WalletService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/wallet")
@AllArgsConstructor
public class WalletController {

    private final WalletService walletService;


    @GetMapping("/balances")
    public ResponseResult<List<WalletBalance>> getAllBalances() {
        Long currentUserId = UserContextHolder.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseResult.error(ResultCode.UNAUTHORIZED);
        }
        List<WalletBalance> balances = walletService.getAllBalances(currentUserId);
        return ResponseResult.success(balances);
    }

    // 【临时测试接口】直接充值，用于MarketMakerBot
    @PostMapping("/deposit")
    public ResponseResult<?> testDeposit(@RequestBody TestDepositRequest request) {
        Long currentUserId = UserContextHolder.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseResult.error(ResultCode.UNAUTHORIZED);
        }
        walletService.deposit(currentUserId, request.getAsset(), request.getAmount(), "manual_test_deposit");
        return ResponseResult.success();
    }

    @Data
    public static class TestDepositRequest {
        private String asset;
        private BigDecimal amount;
    }
}
