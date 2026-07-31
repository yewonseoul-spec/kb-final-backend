package org.scoula.consumption.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsumptionCalendarDTO { // 월별 소비 내역 전체
    private String yearMonth;

    private Long totalSpend; // 이번 달 총 지출

    private Long expectedTotal; // 이번 달 예상 소비 총합

    private List<CategoryDTO> category; // 카테고리별 건수

    private List<DayDTO> days; // 날짜별 소비 내역 / 예상 소비 목록

    @Override
    public String toString() {
        return "ConsumptionCalendarDTO{" +
                "yearMonth='" + yearMonth + '\'' +
                ", totalSpend=" + totalSpend +
                ", expectedTotal=" + expectedTotal +
                ", category=" + category +
                ", days=" + days +
                '}';
    }
}
