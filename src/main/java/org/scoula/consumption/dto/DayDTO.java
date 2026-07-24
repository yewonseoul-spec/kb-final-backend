package org.scoula.consumption.dto;

import java.util.List;

// 일별 소비 내역
public class DayDTO {
    private String date; // "2026-07-05"
    private List<SpendingItemDTO> spendings;
    private List<ExpectedItemDTO> expectedSpendings;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public List<SpendingItemDTO> getSpendings() {
        return spendings;
    }

    public void setSpendings(List<SpendingItemDTO> spendings) {
        this.spendings = spendings;
    }

    public List<ExpectedItemDTO> getExpectedSpendings() {
        return expectedSpendings;
    }

    public void setExpectedSpendings(List<ExpectedItemDTO> expectedSpendings) {
        this.expectedSpendings = expectedSpendings;
    }

    @Override
    public String toString() {
        return "DayDTO{" +
                "date='" + date + '\'' +
                ", spendings=" + spendings +
                ", expectedSpendings=" + expectedSpendings +
                '}';
    }

}

