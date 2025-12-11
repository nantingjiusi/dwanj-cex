package com.remus.dwanjcex.wallet.services;

import com.remus.dwanjcex.wallet.entity.Asset;
import com.remus.dwanjcex.wallet.mapper.AssetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetMapper assetMapper;

    public Asset getAsset(String symbol) {
        return assetMapper.findBySymbol(symbol);
    }

    public List<Asset> getAllAssets() {
        return assetMapper.findAll();
    }

    public void createAsset(Asset asset) {
        if (assetMapper.findBySymbol(asset.getSymbol()) != null) {
            throw new IllegalStateException("Asset with symbol " + asset.getSymbol() + " already exists.");
        }
        assetMapper.insert(asset);
    }
}
