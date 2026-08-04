package org.scoula.stress.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * stress-02: 지출을 줄였을 때의 개선 효과.
 *
 * 위기 유지율은 방어력 점수 계산에 넣지 않는다.
 * 넣으면 위기 시나리오가 평상시보다 점수가 높아지는 모순이 생기기 때문이다.
 * 대신 '이만큼 줄이면 이렇게 나아진다'는 권고로만 쓴다.
 */
@Data
@Builder
public class StressReductionDto {
    private Long reducedSpending;         // 유지율대로 줄였을 때 월 지출
    private Long savingAmount;            // 절감액
    private Double reducedSurvivalMonths;
    private Integer reducedScore;
    private String reducedGrade;
    private List<String> topSavings;      // 절감액이 큰 항목 안내 문구
}