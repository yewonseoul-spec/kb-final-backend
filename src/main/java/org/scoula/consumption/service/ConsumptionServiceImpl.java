package org.scoula.consumption.service;

import lombok.RequiredArgsConstructor;
import org.scoula.consumption.domain.ExpectedSpendingVO;
import org.scoula.consumption.domain.SpendingVO;
import org.scoula.consumption.dto.*;
import org.scoula.consumption.mapper.ConsumptionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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

        // 달력을 조회할 때마다 예정일이 지난 예상 소비 삭제
        consumptionMapper.replaceExpected();

        // 1. DB에서 이번 달 소비 내역 / 예상 소비 목록 조회
        List<SpendingVO> spendings =
                consumptionMapper.selectSpendingByMonth(memberNo, yearMonth);
        System.out.println(" ======== spendings ========= ");
        System.out.println(spendings);
        System.out.println(" ======== expectedList =========");
        List<ExpectedSpendingVO> expectedList =
                consumptionMapper.selectExpectedByMonth(memberNo, yearMonth);

        // 2. 총 지출 / 예상 소비 합계 계산
        long totalSpend = spendings.stream()
                .mapToLong(SpendingVO::getAmount)
                .sum();

        long expectedTotal = expectedList.stream()
                .mapToLong(ExpectedSpendingVO::getExpectedAmount)
                .sum();

        // 3. 카테고리 list
        List<CategoryDTO> categorySummary = new ArrayList<>();

        for (SpendingVO spendingVO : spendings) {
            CategoryDTO categoryDTO = CategoryDTO.builder()
                    .categoryName(spendingVO.getCategoryName())
                    .categoryNo(spendingVO.getCategoryNo())
                    .build();
            categorySummary.add(categoryDTO);
        }


        // 4. 날짜별 소비 내역 / 예상 소비 그룹화
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


        List<DayDTO> days = new ArrayList<>();

        for (String date : allDates) {

            List<SpendingItemDTO> spendingItems = new ArrayList<>();

            for (SpendingVO v : spendingsByDate.getOrDefault(date, List.of())) {
                SpendingItemDTO item = SpendingItemDTO.builder()
                        .spendingNo(v.getSpendingNo())
                        .categoryName(v.getCategoryName())
                        .amount(v.getAmount())
                        .merchant(v.getMerchant())
                        .payMethod(v.getPayMethod())
                        .memo(v.getMemo())
                        .build();
                spendingItems.add(item);
            }


            List<ExpectedItemDTO> expectedItems = new ArrayList<>();

            for (ExpectedSpendingVO v : expectedByDate.getOrDefault(date, List.of())) {
                ExpectedItemDTO item = ExpectedItemDTO.builder()
                        .expectedNo(v.getExpectedNo())
                        .categoryName(v.getCategoryName())
                        .amount(v.getExpectedAmount())
                        .merchant(v.getMerchant())
                        .memo(v.getMemo())
                        .build();
                expectedItems.add(item);
            }

            DayDTO dayDto = DayDTO.builder()
                    .date(date)
                    .spendings(spendingItems)
                    .expectedSpendings(expectedItems)
                    .build();
            days.add(dayDto);
        }

        // 5. 최종 응답 DTO 생성
        ConsumptionCalendarDTO result = ConsumptionCalendarDTO.builder()
                .yearMonth(yearMonth)
                .totalSpend(totalSpend)
                .expectedTotal(expectedTotal)
                .category(categorySummary)
                .days(days)
                .build();

        System.out.println("========== result ==========");
        System.out.println(result);
        return result;
    }

    // 예상 소비 추가
    @Override
    public void addExpected(Long memberNo, ExpectedReqDTO request) {
        ExpectedSpendingVO vo = new ExpectedSpendingVO();
        vo.setMemberNo(memberNo);
        vo.setCategoryNo(request.getCategoryNo());
        vo.setExpectedAmount(request.getExpectedAmount());
        vo.setExpectedDate(LocalDate.parse(request.getExpectedDate()));
        vo.setMerchant(request.getMerchant());
        vo.setMemo(request.getMemo());

        consumptionMapper.insertExpected(vo);
    }

    // 예상 소비 수정
    @Override
    public void updateExpected(Long expectedNo, ExpectedReqDTO request) {
        ExpectedSpendingVO vo = new ExpectedSpendingVO();
        vo.setExpectedNo(expectedNo);
        vo.setCategoryNo(request.getCategoryNo());
        vo.setExpectedAmount(request.getExpectedAmount());
        vo.setExpectedDate(LocalDate.parse(request.getExpectedDate()));
        vo.setMerchant(request.getMerchant());
        vo.setMemo(request.getMemo());

        consumptionMapper.updateExpected(vo);
    }

    // 예상 소비 삭제
    @Override
    public void deleteExpected(Long expectedNo) {
        consumptionMapper.deleteExpected(expectedNo);
    }
}
