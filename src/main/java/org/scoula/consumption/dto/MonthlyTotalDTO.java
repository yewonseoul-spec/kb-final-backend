package org.scoula.consumption.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyTotalDTO {
    private String yearMonth; // "2026-07" 형태

    private int month; // 몇 월

    private long total; // 그 달 총 지출 금액
}
