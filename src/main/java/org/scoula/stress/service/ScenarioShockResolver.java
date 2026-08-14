package org.scoula.stress.service;

import org.scoula.stress.domain.ShockTarget;
import org.scoula.stress.domain.StressScenarioVO;
import org.scoula.stress.dto.CategoryImpactResDto;
import org.scoula.stress.dto.CategorySummaryResDto;
import org.scoula.stress.dto.ScenarioShockDto;
import org.scoula.stress.dto.SpendingSummaryResDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 시나리오를 계산 입력으로 변환한다
 * 생활물가와 주거비는 대상 카테고리가 다르므로 각자의 대상에만 적용한 뒤 합산한다.
 * 같은 금액에 두 비율을 연속으로 곱하지 않는다.
 * 금리 시나리오는 대출 데이터가 자산과 정책과 신청 이력 어디에도 없어 계산하지 않는다.
 */
public final class ScenarioShockResolver {

    private static final String CODE_INFLATION = "INFLATION";
    private static final String CODE_RENT = "RENT";
    private static final String CODE_MEDICAL = "MEDICAL";

    private ScenarioShockResolver() {
    }

    /**
     * 시나리오 목록을 하나의 충격으로 변환한다
     * 여러 축을 함께 선택한 경우 각 축의 대상에만 적용한 뒤 더한다.
     */
    public static ScenarioShockDto resolve(List<StressScenarioVO> scenarios,
                                           SpendingSummaryResDto spendingSummary) {

        if (scenarios == null || scenarios.isEmpty() || spendingSummary == null) {
            return ScenarioShockDto.none();
        }

        // 카테고리별 증가액을 누적한다. 같은 카테고리에 두 축이 걸리는 경우는 없지만
        // 구조상 누적으로 두어야 축이 늘어나도 안전하다
        Map<String, Long> impactMap = new LinkedHashMap<>();
        long oneTimeShock = 0L;
        List<String> descriptions = new ArrayList<>();

        for (StressScenarioVO scenario : scenarios) {
            if (scenario == null || scenario.getScenarioCode() == null) {
                continue;
            }

            String code = scenario.getScenarioCode();

            if (CODE_INFLATION.equals(code)) {
                accumulateRateShock(impactMap, spendingSummary, scenario.getChangeRate(), true);
                descriptions.add("생활물가 상승 " + formatRate(scenario.getChangeRate()));

            } else if (CODE_RENT.equals(code)) {
                accumulateRateShock(impactMap, spendingSummary, scenario.getChangeRate(), false);
                descriptions.add("주거·공과금 상승 " + formatRate(scenario.getChangeRate()));

            } else if (CODE_MEDICAL.equals(code)) {
                long amount = scenario.getFixedAmount() == null ? 0L : scenario.getFixedAmount();
                oneTimeShock += amount;
                descriptions.add(String.format("추가 의료비 %,d원", amount));
            }
        }

        // 카테고리별 영향 목록을 만든다. 증가액이 큰 순으로 보여준다
        List<CategoryImpactResDto> impacts = new ArrayList<>();
        long expenseShock = 0L;

        for (Map.Entry<String, Long> entry : impactMap.entrySet()) {
            long impact = entry.getValue();
            if (impact <= 0L) {
                continue;
            }
            long before = findMonthlyAverage(spendingSummary, entry.getKey());
            impacts.add(CategoryImpactResDto.builder()
                    .categoryName(entry.getKey())
                    .beforeAmount(before)
                    .afterAmount(before + impact)
                    .impact(impact)
                    .build());
            expenseShock += impact;
        }

        impacts.sort((a, b) -> Long.compare(b.getImpact(), a.getImpact()));

        return ScenarioShockDto.builder()
                .recurringExpenseShock(expenseShock)
                .recurringIncomeShock(0L)
                .oneTimeShock(oneTimeShock)
                .appliedDescription(descriptions.isEmpty() ? "평상시" : String.join(" · ", descriptions))
                .categoryImpacts(impacts)
                .build();
    }

    /**
     * 소득 감소 충격을 만든다
     * 소득을 모르는 상태에서는 감소액을 만들 수 없다.
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
     * 대상 카테고리마다 증가액을 계산해 누적한다
     */
    private static void accumulateRateShock(Map<String, Long> impactMap,
                                            SpendingSummaryResDto spendingSummary,
                                            BigDecimal changeRate,
                                            boolean livingCost) {
        if (changeRate == null) {
            return;
        }

        for (CategorySummaryResDto category : spendingSummary.getCategories()) {
            boolean matched = livingCost
                    ? ShockTarget.isLivingCost(category.getCategoryName())
                    : ShockTarget.isHousing(category.getCategoryName());

            if (!matched || category.getMonthlyAverage() <= 0L) {
                continue;
            }

            long impact = BigDecimal.valueOf(category.getMonthlyAverage())
                    .multiply(changeRate)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();

            impactMap.merge(category.getCategoryName(), impact, Long::sum);
        }
    }

    /**
     * 카테고리의 월 환산 금액을 찾는다
     */
    private static long findMonthlyAverage(SpendingSummaryResDto spendingSummary, String categoryName) {
        for (CategorySummaryResDto category : spendingSummary.getCategories()) {
            if (category.getCategoryName().equals(categoryName)) {
                return category.getMonthlyAverage();
            }
        }
        return 0L;
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