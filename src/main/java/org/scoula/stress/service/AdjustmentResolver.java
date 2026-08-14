package org.scoula.stress.service;

import org.scoula.stress.dto.CategoryAdjustReqDto;
import org.scoula.stress.dto.CategorySummaryResDto;
import org.scoula.stress.dto.ScenarioShockDto;
import org.scoula.stress.dto.SpendingSummaryResDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 사용자가 조정한 지출 감소액을 계산한다
 * 감소는 충격이 적용된 뒤의 금액을 기준으로 한다.
 * 물가가 오른 뒤의 식비에서 줄이는 것이지 오르기 전 금액에서 줄이는 것이 아니다.
 * 시스템은 얼마를 줄일 수 있다고 판단하지 않고 사용자가 고른 비율을 그대로 적용한다.
 */
public final class AdjustmentResolver {

    private AdjustmentResolver() {
    }

    /**
     * 조정으로 줄어드는 월 지출 총액을 계산한다
     *
     * @param spendingSummary 소비 집계
     * @param shock           적용된 충격. 카테고리별 증가액이 들어 있다
     * @param adjustments     사용자가 고른 카테고리별 감소율
     */
    public static long resolveReduction(SpendingSummaryResDto spendingSummary,
                                        ScenarioShockDto shock,
                                        List<CategoryAdjustReqDto> adjustments) {

        if (spendingSummary == null || adjustments == null || adjustments.isEmpty()) {
            return 0L;
        }

        // 충격으로 늘어난 금액을 카테고리별로 찾을 수 있게 모아둔다
        Map<String, Long> impactMap = new HashMap<>();
        if (shock != null && shock.getCategoryImpacts() != null) {
            shock.getCategoryImpacts().forEach(
                    impact -> impactMap.put(impact.getCategoryName(), impact.getImpact()));
        }

        // 같은 카테고리가 여러 번 들어와도 마지막 값만 쓴다
        Map<String, BigDecimal> rateMap = new HashMap<>();
        for (CategoryAdjustReqDto adjustment : adjustments) {
            if (adjustment == null || adjustment.getCategoryName() == null) {
                continue;
            }
            BigDecimal rate = adjustment.getReductionRate();
            if (rate == null) {
                continue;
            }
            if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException(
                        "감소율은 0 과 1 사이여야 합니다: " + adjustment.getCategoryName() + " " + rate);
            }
            rateMap.put(adjustment.getCategoryName(), rate);
        }

        long totalReduction = 0L;

        for (CategorySummaryResDto category : spendingSummary.getCategories()) {
            BigDecimal rate = rateMap.get(category.getCategoryName());
            if (rate == null || rate.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            // 충격 적용 후 금액에서 줄인다
            long shockedAmount = category.getMonthlyAverage()
                    + impactMap.getOrDefault(category.getCategoryName(), 0L);

            long reduction = BigDecimal.valueOf(shockedAmount)
                    .multiply(rate)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();

            totalReduction += reduction;
        }

        return totalReduction;
    }
}
