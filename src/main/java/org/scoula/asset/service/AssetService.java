package org.scoula.asset.service;

import org.scoula.asset.dto.AssetDashboardResDTO;

public interface AssetService {
    AssetDashboardResDTO getAssetDashboard(Integer memberNo);
}
