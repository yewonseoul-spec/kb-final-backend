package org.scoula.stress.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 카테고리별 집계 결과
 * 월 환산 평균은 실제로 그 금액을 쓴 달이 없을 수 있으므로 화면에서는
 * 총액과 최근 발생 빈도를 함께 보여준다.
 * @fileName        : CategorySummaryResDto
 * @author          : 박상호
 * @since           : 2026-08-11
 */
@Getter
@Builder
public class CategorySummaryResDto {

    /** 카테고리명 */
    private final String categoryName;

    /** 분석 기간 실제 총액 */
    private final long totalAmount;

    /** 거래가 있었던 달 수. 최근 발생 빈도 */
    private final int occurredMonths;

    /** 월 환산 평균. 총액을 관측 완결월 수로 나눈 값 */
    private final long monthlyAverage;
}