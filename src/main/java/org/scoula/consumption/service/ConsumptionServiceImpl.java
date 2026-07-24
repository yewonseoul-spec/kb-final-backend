package org.scoula.consumption.service;

import lombok.RequiredArgsConstructor;
import org.scoula.consumption.domain.ExpectedSpendingVO;
import org.scoula.consumption.domain.SpendingVO;
import org.scoula.consumption.dto.*;
import org.scoula.consumption.mapper.ConsumptionMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConsumptionServiceImpl implements ConsumptionService {

    private final ConsumptionMapper consumptionMapper;

//    public ConsumptionServiceImpl(ConsumptionMapper consumptionMapper) {
//        this.consumptionMapper = consumptionMapper;
//    }

    @Override
    public ConsumptionCalendarDTO getCal(Long memberNo, String yearMonth) {

        // 1. DB에서 이번 달 지출/예상 소비 목록 조회
        List<SpendingVO> spendings =
                consumptionMapper.selectSpendingByMonth(memberNo, yearMonth);
        System.out.println(" ======== spendings ========= ");
        System.out.println(spendings);
        System.out.println(" ======== expectedList =========");
        List<ExpectedSpendingVO> expectedList =
                consumptionMapper.selectExpectedByMonth(memberNo, yearMonth);

        // 2. 총 지출/예상 소비 합계 계산
        long totalSpend = spendings.stream()
                .mapToLong(SpendingVO::getAmount)
                .sum();

        long expectedTotal = expectedList.stream()
                .mapToLong(ExpectedSpendingVO::getExpectedAmount)
                .sum();

        // 3. 카테고리 list
        List<CategoryDTO> categorySummary = new ArrayList<>();

        for (SpendingVO spendingVO : spendings) {
            CategoryDTO categoryDTO = new CategoryDTO();
            categoryDTO.setCategoryName(spendingVO.getCategoryName());
            categoryDTO.setCategoryNo(spendingVO.getCategoryNo());
            categorySummary.add(categoryDTO);
        }


        // 4. 날짜별 지출/예상 소비 그룹화
        Map<String, List<SpendingVO>> spendingsByDate =
                spendings.stream()
                        .collect(Collectors.groupingBy(
                                v -> v.getSpendingDate().toString()
                        ));

        Map<String, List<ExpectedSpendingVO>> expectedByDate =
                expectedList.stream()
                        .collect(Collectors.groupingBy(
                                v -> v.getExpectedDate().toString()
                        ));

        Set<String> allDates = new TreeSet<>();
        allDates.addAll(spendingsByDate.keySet());
        allDates.addAll(expectedByDate.keySet());


        List<DayDTO> days = new ArrayList<>(); // 모든 날짜별 지출 내역을 모을 리스트


        for (String date : allDates) { // 날짜만 하나씩 꺼내서
            DayDTO dayDto = new DayDTO(); // 날짜별 넣은 dto를 만들고
            dayDto.setDate(date); // dto에 for문에서 꺼낸 date를 넣음.

            List<SpendingItemDTO> spendingItems = new ArrayList<>();

            for (SpendingVO v :
                    spendingsByDate.getOrDefault(date, List.of())) {

                SpendingItemDTO item = new SpendingItemDTO();


                item.setSpendingNo(v.getSpendingNo());
                item.setCategoryName(v.getCategoryName());
                item.setAmount(v.getAmount());
                item.setMerchant(v.getMerchant());

                spendingItems.add(item);
            }


            // 날짜별 예상 소비 목록을 모을 list
            List<ExpectedItemDTO> expectedItems = new ArrayList<>();

            for (ExpectedSpendingVO v :
                    expectedByDate.getOrDefault(date, List.of())) {

                ExpectedItemDTO item = new ExpectedItemDTO();

                item.setExpectedNo(v.getExpectedNo());
                item.setCategoryName(v.getCategoryName());
                item.setAmount(v.getExpectedAmount());
                item.setMerchant(v.getMerchant());

                expectedItems.add(item);
            }

            // 일별 dto에 위에서 모은 "일별 소비 목록(spendingItems)"과 "예상 소비 목록(expectedItems)"을 넣음.
            dayDto.setSpendings(spendingItems);
            dayDto.setExpectedSpendings(expectedItems);

            // days -> 5월의 모든 소비 내역(실제 + 예상)
            // days(dayDTO list)
            days.add(dayDto);
        }

        // 5. 최종 응답 DTO 생성
        ConsumptionCalendarDTO result =
                new ConsumptionCalendarDTO();

        result.setYearMonth(yearMonth);
        result.setTotalSpend(-totalSpend);
        result.setExpectedTotal(expectedTotal);
        result.setCategory(categorySummary);
        result.setDays(days);

        System.out.println("======== result ===========");
        System.out.println(result);
        return result;
    }
}
