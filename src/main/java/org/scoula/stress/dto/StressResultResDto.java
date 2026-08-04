package org.scoula.stress.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

// stress-02: 스트레스 테스트 결과
@Data
@Builder
public class StressResultResDto {

    // OK / NO_SPENDING / NO_ACCOUNT / PROFILE_REQUIRED / SCENARIO_UNAVAILABLE
    private String status;
    private String message;

    private String scenarioCode;
    private String scenarioName;
    private String shockLevel;
    private String shockLabel;
    private String scenarioBasis;      // 이 강도를 왜 이 수치로 잡았는지

    private Integer score;             // 방어력 점수 0~100
    private String grade;              // 안전 / 주의 / 위험
    private String gradeAction;        // 권장 행동
    private Double survivalMonths;     // 생존 가능 개월. 점수가 100이어도 이 값은 계속 변한다

    private Long balance;              // 사용 가능 잔액
    private Long monthlySpending;      // 평상시 월 지출
    private Long increaseAmount;       // 시나리오 증가분
    private Long crisisSpending;        // 위기 시 월 지출

    private Long fixedTotal;           // 고정비 합계
    private Long variableTotal;        // 변동비 합계
    private Long adjustableTotal;      // 조절 가능 지출 합계

    private List<StressBreakdownDto> breakdown;
    private StressReductionDto reduction;
    private List<String> basis;        // 계산 근거
}
