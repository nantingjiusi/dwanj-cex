package com.remus.dwanjcex.wallet.controller;

import com.remus.dwanjcex.wallet.entity.Asset;
import com.remus.dwanjcex.wallet.entity.result.ResponseResult;
import com.remus.dwanjcex.wallet.services.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @GetMapping
    public ResponseResult<List<Asset>> getAllAssets() {
        return ResponseResult.success(assetService.getAllAssets());
    }
}
