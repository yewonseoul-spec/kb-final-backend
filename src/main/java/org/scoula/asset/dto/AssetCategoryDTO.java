package org.scoula.asset.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetCategoryDTO {
    private String categoryName; // 예: 예ㆍ적금, 입출금, 청약
    private long amount;
}
