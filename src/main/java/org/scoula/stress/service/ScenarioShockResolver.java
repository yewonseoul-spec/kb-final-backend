package org.scoula.stress.service;

import org.scoula.stress.domain.ShockTarget;
import org.scoula.stress.domain.StressScenarioVO;
import org.scoula.stress.dto.CategorySummaryResDto;
import org.scoula.stress.dto.ScenarioShockDto;
import org.scoula.stress.dto.SpendingSummaryResDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 시나리오를 계산 입력으로 변환한다
 * 생활물가와 주거비는 대상 카테고리가 다르므로 각자의 대상에만 적용한 뒤 합산한다.
 * 같은 금액에 두 비율을 연속으로 곱하지 않는다.
 * 금리 시나리오는 대출 데이터가 자산과 정책과 신청 이력 어디에도 없어 계산하지 않는다.
 * @fileName        : ScenarioShockResolver
 * @author          : 박상호
 * @since           : 2026-08-12
 */
public final class ScenarioShockResolver {

    private static final String CODE_INFLATION = "INFLATION";
    private static final String CODE_RENT = "RENT";
    private static final String CODE_MEDICAL = "MEDICAL";
    private static final String CODE_COMPLEX = "COMPLEX";

    private ScenarioShockResolver() {
    }

    /**
     * 시나리오 목록을 하나의 충격으로 변환한다
     * 복합 시나리오는 서로 다른 축을 함께 선택한 상태이므로 여러 건을 받는다.
     */
    public static ScenarioShockDto resolve(List<StressScenarioVO> scenarios,
                                           SpendingSummaryResDto spendingSummary) {

        if (scenarios == null || scenarios.isEmpty() || spendingSummary == null) {
            return ScenarioShockDto.none();
        }

        long expenseShock = 0L;
        long oneTimeShock = 0L;
        List<String> descriptions = new ArrayList<>();

        for (StressScenarioVO scenario : scenarios) {
            if (scenario == null || scenario.getScenarioCode() == null) {
                continue;
            }

            String code = scenario.getScenarioCode();

            if (CODE_INFLATION.equals(code)) {
                long amount = calculateRateShock(
                        spendingSummary, scenario.getChangeRate(), true);
                expenseShock += amount;
                descriptions.add("생활물가 상승 " + formatRate(scenario.getChangeRate()));

            } else if (CODE_RENT.equals(code)) {
                long amount = calculateRateShock(
                        spendingSummary, scenario.getChangeRate(), false);
                expenseShock += amount;
                descriptions.add("주거·공과금 상승 " + formatRate(scenario.getChangeRate()));

            } else if (CODE_MEDICAL.equals(code)) {
                long amount = scenario.getFixedAmount() == null ? 0L : scenario.getFixedAmount();
                oneTimeShock += amount;
                descriptions.add("추가 의료비 " + amount + "원");
            }
            // COMPLEX 는 개별 시나리오를 조합해 호출하므로 여기서 따로 처리하지 않는다
            // RATE 는 대출 데이터가 없어 계산하지 않는다
        }

        return ScenarioShockDto.builder()
                .recurringExpenseShock(expenseShock)
                .recurringIncomeShock(0L)
                .oneTimeShock(oneTimeShock)
                .appliedDescription(descriptions.isEmpty() ? "평상시" : String.join(" · ", descriptions))
                .build();
    }

    /**
     * 소득 감소 충격을 만든다
     * 소득을 모르는 상태에서는 감소액을 만들 수 없다.
     *
     * @param monthlyIncome 등록 월 소득. null 이면 0 을 반환한다
     * @param reductionRate 감소 비율. 0 이상 1 이하
     */
    public static long resolveIncomeShock(Long monthlyIncome, BigDecimal reductionRate) {
        if (monthlyIncome == null || reductionRate == null) {
            return 0L;
        }
        if (reductionRate.compareTo(BigDecimal.ZERO) < 0
                || reductionRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("소득 감소 비율은 0 과 1 사이여야 합니다: " + reductionRate);
        }
        return BigDecimal.valueOf(monthlyIncome)
                .multiply(reductionRate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    /**
     * 비율 충격 금액을 계산한다
     *
     * @param livingCost true 면 생활물가 대상, false 면 주거 대상
     */
    private static long calculateRateShock(SpendingSummaryResDto spendingSummary,
                                           BigDecimal changeRate,
                                           boolean livingCost) {
        if (changeRate == null) {
            return 0L;
        }

        long targetAmount = 0L;
        for (CategorySummaryResDto category : spendingSummary.getCategories()) {
            boolean matched = livingCost
                    ? ShockTarget.isLivingCost(category.getCategoryName())
                    : ShockTarget.isHousing(category.getCategoryName());
            if (matched) {
                targetAmount += category.getMonthlyAverage();
            }
        }

        return BigDecimal.valueOf(targetAmount)
                .multiply(changeRate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    /**
     * 비율을 화면 문구로 바꾼다
     */
    private static String formatRate(BigDecimal changeRate) {
        if (changeRate == null) {
            return "0%";
        }
        return changeRate.multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "%";
    }
}
