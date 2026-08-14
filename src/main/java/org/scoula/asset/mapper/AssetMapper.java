package org.scoula.asset.mapper;

import org.scoula.asset.dto.AccountDTO;
import org.scoula.asset.dto.HeldProductDTO;
import org.scoula.asset.dto.MaturityDTO;

import java.util.List;

public interface AssetMapper {
    Long selectTotalAsset(Integer memberNo);

    List<AccountDTO> selectAccounts(Integer memberNo);

    List<MaturityDTO> selectMaturities(Integer memberNo);

    List<HeldProductDTO> selectHeldProductAmounts(Integer memberNo);
}
