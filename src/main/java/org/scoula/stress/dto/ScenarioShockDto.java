package org.scoula.stress.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.ArrayList;
import java.util.List;

/**
 * 시나리오를 계산 입력으로 변환한 결과
 * 반복 충격과 일회성 충격을 분리한다. 의료비처럼 한 번만 발생하는 비용을
 * 월 지출에 더하면 매달 반복되는 것으로 계산되기 때문이다.
 */
@Getter
@Builder
public class ScenarioShockDto {

    /** 반복 지출 증가액 */
    private final long recurringExpenseShock;

    /** 반복 소득 감소액 */
    private final long recurringIncomeShock;

    /** 일회성 충격 금액 */
    private final long oneTimeShock;

    /** 화면에 보여줄 적용 내역 */
    private final String appliedDescription;

    /** 카테고리별 충격 영향 */
    private final List<CategoryImpactResDto> categoryImpacts;

    /**
     * 충격이 하나도 없는 평상시 상태를 반환한다
     */
    public static ScenarioShockDto none() {
        return ScenarioShockDto.builder()
                .recurringExpenseShock(0L)
                .recurringIncomeShock(0L)
                .oneTimeShock(0L)
                .appliedDescription("평상시")
                .categoryImpacts(new ArrayList<>())
                .build();
    }
}