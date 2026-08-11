package org.scoula.consumption.service;

import org.scoula.consumption.dto.MonthlyTotalDTO;

import java.util.List;

public interface MonthlyTrendService {
    // 이번 달을 포함하여 최근 n달 치의 달별 총 지출을 오래된 달부터 최신 달 순서로 돌려준다
    List<MonthlyTotalDTO> getMonthlyTrend(Integer memberNo);
}
