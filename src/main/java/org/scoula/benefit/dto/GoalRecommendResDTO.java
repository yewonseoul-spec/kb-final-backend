package org.scoula.benefit.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GoalRecommendResDTO {

    /*
     * 목표를 설정하지 않았으면 null 이다.
     * 화면에서 "목표 미설정"과 "조건에 맞는 혜택 없음"을
     * 가르는 기준이므로 빈 목록으로 뭉뚱그리면 안 된다.
     */
    private String goalType;

    // 1차 : 목표에 직접 맞닿는 중분류
    private List<BenefitListResDTO> primaryBenefits;

    // 2차 : 함께 보면 좋은 중분류
    private List<BenefitListResDTO> secondaryBenefits;
}
