package org.scoula.stress.dto;

import lombok.Builder;
import lombok.Data;

/**
 * stress-02: 카테고리별 지출 구성 한 줄.
 *
 * spendingType은 별도 분류표가 아니라 유지율에서 파생된다.
 * 유지율 100%면 못 줄이는 고정비, 50% 미만이면 위기 시 대부분 접는 지출이다.
 */
@Data
@Builder
public class StressBreakdownDto {
    private String categoryName;
    private Long monthlyAmount;
    private Double keepRate;        // 위기 시 유지율
    private String spendingType;    // 고정비 / 변동비 / 조절 가능
    private Long reducibleAmount;   // 줄일 수 있는 금액
    private boolean affected;       // 이번 시나리오의 영향을 받는지
}
