package org.scoula.stress.service;

import org.scoula.stress.domain.AnalysisWindow;
import org.scoula.stress.dto.CategoryMonthlyResDto;
import org.scoula.stress.dto.CategorySummaryResDto;
import org.scoula.stress.dto.SpendingSummaryResDto;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 소비 집계
 * 외부 의존성이 없는 순수 계산 클래스다.
 * 분모는 정상 관측된 완결월 수이지 거래가 있었던 달의 수가 아니다. 거래월로 나누면
 * 여행처럼 산발적인 지출이 매달 반복되는 비용처럼 부풀려진다.
 * 거래가 없는 정상 관측월은 0 원으로 분모에 포함하며, 금액 크기에 근거한 이상치 처리는 하지 않는다.
 * 검토한 방법이 전부 지출을 낮추는 방향이라 실제보다 안전해 보이게 만들기 때문이다.
 * @fileName        : SpendingAggregator
 * @author          : 박상호
 * @since           : 2026-08-11
 */
public final class SpendingAggregator {

    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 누적 배열 인덱스. 총액 */
    private static final int TOTAL_INDEX = 0;

    /** 누적 배열 인덱스. 거래 발생 월 수 */
    private static final int OCCURRED_INDEX = 1;

    private SpendingAggregator() {
    }

    /**
     * 카테고리별 월별 조회 결과를 집계한다
     *
     * @param analysisWindow 분석 창
     * @param monthlyRows    조회 결과. 거래가 있는 행만 들어 있다
     */
    public static SpendingSummaryResDto aggregate(AnalysisWindow analysisWindow,
                                                  List<CategoryMonthlyResDto> monthlyRows) {

        if (analysisWindow == null) {
            throw new IllegalArgumentException("분석 창이 없습니다");
        }

        int observationMonths = analysisWindow.getObservationMonths();

        if (observationMonths == 0) {
            return SpendingSummaryResDto.builder()
                    .observationMonths(0)
                    .monthlySpending(0L)
                    .categories(new ArrayList<>())
                    .build();
        }

        Set<String> targetMonths = new HashSet<>();
        for (YearMonth month : analysisWindow.getMonths()) {
            targetMonths.add(month.format(YEAR_MONTH_FORMAT));
        }

        // 카테고리별 총액과 거래 발생 월 수를 모은다
        Map<String, long[]> categoryAccumulator = new LinkedHashMap<>();
        long grandTotal = 0L;

        if (monthlyRows != null) {
            for (CategoryMonthlyResDto row : monthlyRows) {
                if (row == null || row.getCategoryName() == null) {
                    continue;
                }
                if (!targetMonths.contains(row.getYearMonth())) {
                    continue;
                }
                long[] accumulated = categoryAccumulator.computeIfAbsent(
                        row.getCategoryName(), key -> new long[]{0L, 0L});
                accumulated[TOTAL_INDEX] += row.getAmount();
                if (row.getAmount() != 0L) {
                    accumulated[OCCURRED_INDEX] += 1L;
                }
                grandTotal += row.getAmount();
            }
        }

        // 월 환산한다. 분모는 언제나 정상 관측 완결월 수다
        List<CategorySummaryResDto> categories = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : categoryAccumulator.entrySet()) {
            long totalAmount = entry.getValue()[TOTAL_INDEX];
            int occurredMonths = (int) entry.getValue()[OCCURRED_INDEX];
            long monthlyAverage = Math.round((double) totalAmount / observationMonths);

            categories.add(CategorySummaryResDto.builder()
                    .categoryName(entry.getKey())
                    .totalAmount(totalAmount)
                    .occurredMonths(occurredMonths)
                    .monthlyAverage(monthlyAverage)
                    .build());
        }

        // 지출액이 큰 순으로 정렬한다
        // 시스템이 조절 가능 여부를 분류하지 않고 사용자가 상위 항목부터 보고 직접 고르게 하기 위해서다
        categories.sort(Comparator.comparingLong(CategorySummaryResDto::getTotalAmount).reversed());

        long monthlySpending = Math.round((double) grandTotal / observationMonths);

        return SpendingSummaryResDto.builder()
                .observationMonths(observationMonths)
                .startMonth(analysisWindow.getStartMonth())
                .endMonth(analysisWindow.getEndMonth())
                .monthlySpending(monthlySpending)
                .categories(categories)
                .build();
    }
}
