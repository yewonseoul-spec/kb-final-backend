package org.scoula.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor

//engine-06: 추천 조합 하나
public class CombinationResDto {
    private List<BenefitResDto> benefits; // 조합 구성 정책
    private double combinationScore;  //조합 점수
    private double averageScore; // 구성 정책 평균 점수
    private int distinctCategoryCount; //서로 다른 카테고리 수
    private List<String> warnings; //조합 내부 경고 문구
    private List<String> scoreDetail; //점수 산출 근거
}
