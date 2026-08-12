package org.scoula.consumption.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryAmountDTO {
    // 이번 달 이 카테고리에 얼마나 소비했는지를 나타내는 카테고리별 소비 금액 리스트
    private String categoryName;
    private long amount;
}
