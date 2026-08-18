package org.scoula.consumption.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.scoula.consumption.domain.SpendingVO;
import org.scoula.consumption.mapper.ConsumptionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SpendingSummaryServiceImpl implements SpendingSummaryService {

    private static final int AVERAGE_MONTHS = 3;

    private final ConsumptionMapper consumptionMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpendingSummaryServiceImpl(ConsumptionMapper consumptionMapper) {
        this.consumptionMapper = consumptionMapper;
    }

    @Override
    public String buildSummaryText(Integer memberNo, String yearMonth) {

        LocalDate yesterday = LocalDate.now().minusDays(1);
        int sameDayOfMonth = yesterday.getDayOfMonth();
        YearMonth thisYearMonth = YearMonth.parse(yearMonth);

        // 이번 달 / 지난 달 소비 내역을 각각 가져온다
//        List<SpendingVO> thisMonthList = consumptionMapper.selectSpendingByMonth(memberNo, yearMonth);
//        YearMonth lastYearMonth = YearMonth.parse(yearMonth).minusMonths(1);
//        List<SpendingVO> lastMonthList = consumptionMapper.selectSpendingByMonth(memberNo, lastYearMonth.toString());

        // 이번 달 데이터 중에서 어제까지의 데이터만 남긴다
        List<SpendingVO> thisMonthUntilYesterday = getSpendingUntilSameDay(memberNo, thisYearMonth, sameDayOfMonth);

        // 지난 달 데이터 중에서 어제와 같은 날짜까지의 데이터만 남긴다
//        int sameDayOfMonth = yesterday.getDayOfMonth();
//        int lastMonthLastDay = Math.min(sameDayOfMonth, lastYearMonth.lengthOfMonth());
//        LocalDate lastMonthCutoff = lastYearMonth.atDay(lastMonthLastDay);

        YearMonth lastYearMonth = thisYearMonth.minusMonths(1);
        List<SpendingVO> lastMonthUntilSameDay = getSpendingUntilSameDay(memberNo, lastYearMonth, sameDayOfMonth);

//        List<SpendingVO> lastMonthUntilSameDay = lastMonthList.stream()
//                .filter(v -> !v.getSpendingDate().isAfter(lastMonthCutoff))
//                .collect(Collectors.toList());

        // 총합
        long thisTotal = sumAmount(thisMonthUntilYesterday);
        long lastTotal = sumAmount(lastMonthUntilSameDay);

        // 카테고리별로 얼마씩 썼는지 묶는다
        Map<String, Long> thisByCategory = groupByCategory(thisMonthUntilYesterday);
        Map<String, Long> lastByCategory = groupByCategory(lastMonthUntilSameDay);

        // 최근 3개월(오늘과 같은 날짜까지)의 카테고리별 평균을 구한다
        Map<String, Long> categoryAverage = calculateCategoryAverage(memberNo, thisYearMonth, sameDayOfMonth);

        long totalDiff = Math.abs(thisTotal - lastTotal);
        
        // 글로 조립한다
//        StringBuilder sb = new StringBuilder();
//        sb.append(yearMonth).append(" 소비 요약 (").append(yesterday).append(" 기준)\n");
//        sb.append("- 이번 달 총 지출: ").append(thisTotal).append("원\n");
//        sb.append("- 지난 달 총 지출: ").append(lastTotal).append("원\n");
//        sb.append("\n카테고리별 지출 (지난달 대비):\n");
//
//        List<Map.Entry<String, Long>> sortedCategories = thisByCategory.entrySet().stream()
//                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
//                .collect(Collectors.toList());
//
//        for (Map.Entry<String, Long> entry : sortedCategories) {
//            String category = entry.getKey();
//            long amount = entry.getValue();
//            long lastAmount = lastByCategory.getOrDefault(category, 0L);
//
//            sb.append("- ").append(category).append(": ").append(amount).append("원");
//
//            if (lastAmount > 0) {
//                double changeRate = ((double) (amount - lastAmount) / lastAmount) * 100;
//                sb.append(String.format(" (지난 달 대비 %+.0f%%)", changeRate));
//            } else {
//                sb.append(" (지난 달의 지출이 없습니다.)");
//            }
//            sb.append("\n");
//        }
//
//        return sb.toString();

        // 데이터를 Map으로 조립하고 JSON 문자열로 바꾼다
        List<Map<String, Object>> categoryList = new ArrayList<>();

        List<Map.Entry<String, Long>> sortedCategories = thisByCategory.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toList());

        for (Map.Entry<String, Long> entry : sortedCategories) {
            String category = entry.getKey();
            long amount = entry.getValue();
            long lastAmount = lastByCategory.getOrDefault(category, 0L);
            long avgAmount = categoryAverage.getOrDefault(category, 0L);

            Map<String, Object> categoryData = new LinkedHashMap<>();
            categoryData.put("카테고리", category);
            categoryData.put("이번달금액", amount);
            categoryData.put("지난달같은기간대비", directionTag(amount, lastAmount));
            categoryData.put("지난달대비차이", amount - lastAmount);
            categoryData.put("최근3개월평균금액", avgAmount);
            categoryData.put("평균대비", avgAmount > 0 ? directionTag(amount, avgAmount) : "데이터없음");
            categoryList.add(categoryData);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("기준일", yesterday.toString());
        summary.put("이번달1일부터며칠까지", sameDayOfMonth);
        summary.put("이번달총지출", thisTotal);
        summary.put("지난달같은기간총지출", lastTotal);
        summary.put("총지출증감액", totalDiff);
        summary.put("전체지출방향", directionTag(thisTotal, lastTotal));
        summary.put("카테고리별지출", categoryList);

        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            throw new RuntimeException("소비 요약 데이터를 만드는 중 오류가 발생했어요.", e);
        }
    }

    // targetMonth의 소비내역 중 "1일부터 dayOfMonth일까지"만 걸러서 돌려준다.
    private List<SpendingVO> getSpendingUntilSameDay(Integer memberNo, YearMonth targetMonth, int dayOfMonth) {
        List<SpendingVO> monthList = consumptionMapper.selectSpendingByMonth(memberNo, targetMonth.toString());

        int safeDay = Math.min(dayOfMonth, targetMonth.lengthOfMonth());
        LocalDate cutoff = targetMonth.atDay(safeDay);

        return monthList.stream()
                .filter(v -> !v.getSpendingDate().isAfter(cutoff))
                .collect(Collectors.toList());
    }

    private long sumAmount(List<SpendingVO> list) {
        return list.stream().mapToLong(SpendingVO::getAmount).sum();
    }

    private Map<String, Long> groupByCategory(List<SpendingVO> list) {
        return list.stream()
                .collect(Collectors.groupingBy(
                        SpendingVO::getCategoryName,
                        Collectors.summingLong(SpendingVO::getAmount)
                ));
    }

    // 최근 AVERAGE_MONTHS달의 같은 날짜까지의 데이터를 모아서, 카테고리별 월 평균 지출을 계산한다.
    private Map<String, Long> calculateCategoryAverage(Integer memberNo, YearMonth thisYearMonth, int dayOfMonth) {
        Map<String, List<Long>> amountsByCategory = new HashMap<>();

        for (int i = 1; i <= AVERAGE_MONTHS; i++) {
            YearMonth targetMonth = thisYearMonth.minusMonths(i);
            List<SpendingVO> monthList = getSpendingUntilSameDay(memberNo, targetMonth, dayOfMonth);
            Map<String, Long> monthByCategory = groupByCategory(monthList);

            for (Map.Entry<String, Long> entry : monthByCategory.entrySet()) {
                amountsByCategory
                        .computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                        .add(entry.getValue());
            }
        }

        Map<String, Long> average = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : amountsByCategory.entrySet()) {
            long sum = entry.getValue().stream().mapToLong(Long::longValue).sum();
            long avg = sum / entry.getValue().size();
            average.put(entry.getKey(), avg);
        }

        return average;
    }

    // amount가 baseline보다 큰지/작은지/같은지/새로 생긴 건지 판단한다.
    private String directionTag(long amount, long baseline) {
        if (baseline == 0) return "신규";
        if (amount > baseline) return "증가";
        if (amount < baseline) return "감소";
        return "동일";
    }
}
