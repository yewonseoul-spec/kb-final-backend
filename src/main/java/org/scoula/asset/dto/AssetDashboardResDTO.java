package org.scoula.asset.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetDashboardResDTO {
    private Long totalAsset; // 계좌 잔액의 합 + 금융 상품 보유 금액의 합
    private List<AccountDTO> accounts; // 계좌별 잔액 목록
    private List<MaturityDTO> maturities; // 가입 금융 상품 만기일 목록

}
