package org.scoula.consumption.service;

import org.scoula.consumption.domain.SpendingVO;
import org.scoula.consumption.dto.MonthlyTotalDTO;
import org.scoula.consumption.mapper.ConsumptionMapper;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
public class MonthlyTrendServiceImpl implements MonthlyTrendService {
    // 그래프에 몇 달치를 보여줄지
    private static final int MONTHS_TO_SHOW = 5;

    private final ConsumptionMapper consumptionMapper;

    public MonthlyTrendServiceImpl(ConsumptionMapper consumptionMapper) {
        this.consumptionMapper = consumptionMapper;
    }

    @Override
    public List<MonthlyTotalDTO> getMonthlyTrend(Integer memberNo) {
        List<MonthlyTotalDTO> result = new ArrayList<>();
        YearMonth thisMonth = YearMonth.now();

        for (int i = MONTHS_TO_SHOW - 1; i >= 0; i--) {
            YearMonth targetMonth = thisMonth.minusMonths(i);

            List<SpendingVO> spendingList = consumptionMapper.selectSpendingByMonth(memberNo, targetMonth.toString());

            long total = spendingList.stream()
                    .mapToLong(SpendingVO::getAmount)
                    .sum();

            MonthlyTotalDTO monthlyTotal = MonthlyTotalDTO.builder()
                    .yearMonth(targetMonth.toString())
                    .month(targetMonth.getMonthValue())
                    .total(total)
                    .build();

            result.add(monthlyTotal);
        }

        return result;
    }
}
