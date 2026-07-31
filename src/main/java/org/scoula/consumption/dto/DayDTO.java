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
public class DayDTO { // 일별 소비 내역
    private String date; // "2026-07-05"

    private List<SpendingItemDTO> spendings;

    private List<ExpectedItemDTO> expectedSpendings;

    @Override
    public String toString() {
        return "DayDTO{" +
                "date='" + date + '\'' +
                ", spendings=" + spendings +
                ", expectedSpendings=" + expectedSpendings +
                '}';
    }
}
