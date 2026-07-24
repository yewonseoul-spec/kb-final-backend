package org.scoula.consumption.dto;

import java.util.List;

// 월별 소비 내역 전체
public class ConsumptionCalendarDTO {

    private String yearMonth;
    private Long totalSpend; // 이번 달 총 지출
    private Long expectedTotal; // 이번 달 예상 소비 총합
    private List<CategoryDTO> category; // 카테고리별 건수
    private List<DayDTO> days; // 날짜별 지출/예상 지출 목록

    public String getYearMonth() {
        return yearMonth;
    }

    public void setYearMonth(String yearMonth) {
        this.yearMonth = yearMonth;
    }

    public Long getTotalSpend() {
        return totalSpend;
    }

    public void setTotalSpend(Long totalSpend) {
        this.totalSpend = totalSpend;
    }

    public Long getExpectedTotal() {
        return expectedTotal;
    }

    public void setExpectedTotal(Long expectedTotal) {
        this.expectedTotal = expectedTotal;
    }

    public List<CategoryDTO> getCategory() {
        return category;
    }

    public void setCategory(List<CategoryDTO> category) {
        this.category = category;
    }

    public List<DayDTO> getDays() {
        return days;
    }

    public void setDays(List<DayDTO> days) {
        this.days = days;
    }

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
