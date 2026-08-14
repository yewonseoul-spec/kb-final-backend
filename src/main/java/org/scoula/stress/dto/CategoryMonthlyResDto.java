package org.scoula.stress.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 카테고리별 월별 합계 조회 결과
 * GROUP BY 는 거래가 없는 달의 행을 만들어주지 않는다. 따라서 이 목록에 없는 달은
 * 0 원으로 채워야 하며 행의 개수를 분모로 삼으면 안 된다.
 * @fileName        : CategoryMonthlyResDto
 * @author          : 박상호
 * @since           : 2026-08-11
 */
@Getter
@Setter
@NoArgsConstructor
public class CategoryMonthlyResDto {

    /** 연월. yyyy-MM 형식 */
    private String yearMonth;

    /** 카테고리명 */
    private String categoryName;

    /** 해당 월 해당 카테고리의 실제 합계 */
    private long amount;

    @Builder
    public CategoryMonthlyResDto(String yearMonth, String categoryName, long amount) {
        this.yearMonth = yearMonth;
        this.categoryName = categoryName;
        this.amount = amount;
    }
}