package org.scoula.benefit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumptionRecommendResDTO {

    // "교통, 교육 소비패턴을 바탕으로~~ 메세지표시"
    private String message;

    // 사용자의 주요 소비 카테고리
    private List<String> spendingCategories;

    // 연결된 혜택 중분류
    private List<String> benefitCategories;

    private Integer totalCount;

    private List<BenefitListResDTO> benefits;
}