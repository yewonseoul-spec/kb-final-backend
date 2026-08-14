package org.scoula.asset.service;

import org.scoula.asset.dto.*;
import org.scoula.asset.mapper.AssetMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public AssetRatioResDTO getAssetRatio(Integer memberNo) {

        Map<String, Long> totalsByCategory = new HashMap<>();

        // 입출금 계좌들의 잔액을 다 더해서 입출금 카테고리로 넣는다
        List<AccountDTO> accounts = assetMapper.selectAccounts(memberNo);
        long accountTotal = 0;
        for (AccountDTO account : accounts) {
            accountTotal += account.getBalance();
        }
        if (accountTotal > 0) {
            totalsByCategory.put("입출금", accountTotal);
        }

        // 가입한 금융 상품들을 상품 종류별로 가져와서 화면에 보여줄 한글 카테고리 이름으로 바꿔가며 더한다
        List<HeldProductDTO> heldProducts = assetMapper.selectHeldProductAmounts(memberNo);

        for (HeldProductDTO held : heldProducts) {
            String categoryName = toCategoryName(held.getProductType());

            long soFar = totalsByCategory.getOrDefault(categoryName, 0L);
            totalsByCategory.put(categoryName, soFar + held.getAmount());
        }

        // 담긴 걸 DTO 리스트로 옮기고, 전체 합계를 구한다
        List<AssetCategoryDTO> categories = new ArrayList<>();
        long totalAsset = 0;

        for (Map.Entry<String, Long> entry : totalsByCategory.entrySet()) {
            categories.add(AssetCategoryDTO.builder()
                    .categoryName(entry.getKey())
                    .amount(entry.getValue())
                    .build());

            totalAsset += entry.getValue();
        }

        // 금액이 큰 순서대로 정렬
        categories.sort((a, b) -> Long.compare(b.getAmount(), a.getAmount()));

        return AssetRatioResDTO.builder()
                .totalAsset(totalAsset)
                .categories(categories)
                .build();
    }

    private String toCategoryName(String productType) {
        switch (productType) {
            case "DEPOSIT":
            case "SAVINGS":
                return "예ㆍ적금";
            case "SUBSCRIPTION":
                return "청약";
            case "INSURANCE":
                return "보험ㆍ공제";
            case "PENSION":
                return "퇴직연금";
            default:
                return "기타";
        }
    }

    @Override
    public List<AccountDTO> getAccountBalances(Integer memberNo) {
        return assetMapper.selectAccounts(memberNo);
    }
}
