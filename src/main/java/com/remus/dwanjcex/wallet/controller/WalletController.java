package com.remus.dwanjcex.wallet.controller;

import com.remus.dwanjcex.wallet.entity.UserBalance;
import com.remus.dwanjcex.wallet.entity.dto.WalletDto;
import com.remus.dwanjcex.wallet.entity.result.ResponseResult;
import com.remus.dwanjcex.wallet.entity.result.ResultCode;
import com.remus.dwanjcex.wallet.mapper.UserBalanceMapper;
import com.remus.dwanjcex.wallet.services.WalletService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/wallet")
public class WalletController {
    private final WalletService walletService;
    private final UserBalanceMapper balanceMapper;

    public WalletController(WalletService walletService, UserBalanceMapper balanceMapper) {
        this.walletService = walletService;
        this.balanceMapper = balanceMapper;
    }

    @PostMapping("/deposit")
    public ResponseResult<String> deposit(@RequestBody WalletDto dto) {
        walletService.deposit(dto.getUserId(), dto.getAsset(), dto.getAmount(), dto.getRef());
        return ResponseResult.success("Deposit successful");
    }

    @PostMapping("/freeze")
    public ResponseResult<String> freeze(@RequestBody WalletDto dto) {
        boolean ok = walletService.freeze(dto.getUserId(), dto.getAsset(), dto.getAmount(), dto.getRef());
        return ok ? ResponseResult.success("frozen") : ResponseResult.error(ResultCode.INSUFFICIENT_BALANCE,"insufficient_balance");
    }

    @PostMapping("/unfreeze")
    public ResponseResult<String> unfreeze(@RequestBody WalletDto dto) {
        boolean ok = walletService.unfreeze(dto.getUserId(), dto.getAsset(), dto.getAmount(), dto.getRef());
        return ok ? ResponseResult.success("unfrozen") : ResponseResult.error(ResultCode.NO_FROZEN,"no_frozen");
    }

    @GetMapping("/balances/{userId}")
    public ResponseResult<List<UserBalance>> balances(@PathVariable Long userId) {
        return ResponseResult.success(balanceMapper.selectByUserId(userId));
    }
}

