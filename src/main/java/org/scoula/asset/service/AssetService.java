package org.scoula.asset.service;

import org.scoula.asset.dto.AccountDTO;
import org.scoula.asset.dto.AssetDashboardResDTO;

import java.util.List;

public interface AssetService {
    AssetDashboardResDTO getAssetDashboard(Integer memberNo);

    List<AccountDTO> getAccountBalances(Integer memberNo);
}
