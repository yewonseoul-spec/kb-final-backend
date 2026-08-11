package org.scoula.stress.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.YearMonth;
import java.util.Collections;
import java.util.List;

/**
 * 소비 집계 결과
 * 월 환산 지출은 분석 기간 전체 소비를 정상 관측 완결월 수로 나눈 값이다.
 * @fileName        : SpendingSummaryResDto
 * @author          : 박상호
 * @since           : 2026-08-11
 */
@Getter
public class SpendingSummaryResDto {

    /** 정상 관측된 완결월 수 */
    private final int observationMonths;

    /** 분석 시작월 */
    private final YearMonth startMonth;

    /** 분석 종료월 */
    private final YearMonth endMonth;

    /** 월 환산 지출 */
    private final long monthlySpending;

    /** 카테고리별 집계. 총액 내림차순 */
    private final List<CategorySummaryResDto> categories;

    @Builder
    public SpendingSummaryResDto(int observationMonths,
                                 YearMonth startMonth,
                                 YearMonth endMonth,
                                 long monthlySpending,
                                 List<CategorySummaryResDto> categories) {

        this.observationMonths = observationMonths;
        this.startMonth = startMonth;
        this.endMonth = endMonth;
        this.monthlySpending = monthlySpending;
        this.categories = categories == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(categories);
    }

    /**
     * 관측 완결월이 없어 계산이 불가능한 상태인지 확인한다
     */
    public boolean isInsufficientHistory() {
        return observationMonths == 0;
    }
}