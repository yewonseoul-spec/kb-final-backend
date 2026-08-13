package org.scoula.asset.service;

import org.scoula.asset.dto.AccountDTO;
import org.scoula.asset.dto.AssetDashboardResDTO;
import org.scoula.asset.dto.MaturityDTO;
import org.scoula.asset.mapper.AssetMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("assetService")
public class AssetServiceImpl implements AssetService {

    @Autowired
    private AssetMapper assetMapper;

    @Override
    public AssetDashboardResDTO getAssetDashboard(Integer memberNo) {
        Long totalAsset = assetMapper.selectTotalAsset(memberNo);
        List<AccountDTO> accounts = assetMapper.selectAccounts(memberNo);
        List<MaturityDTO> maturities = assetMapper.selectMaturities(memberNo);

        return new AssetDashboardResDTO(totalAsset, accounts, maturities);
    }

    @Override
    public List<AccountDTO> getAccountBalances(Integer memberNo) {
        return assetMapper.selectAccounts(memberNo);
    }
}
