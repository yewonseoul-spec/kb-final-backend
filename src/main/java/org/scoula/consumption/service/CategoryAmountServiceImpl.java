package org.scoula.consumption.service;

import org.scoula.consumption.domain.SpendingVO;
import org.scoula.consumption.dto.CategoryAmountDTO;
import org.scoula.consumption.mapper.ConsumptionMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CategoryAmountServiceImpl implements CategoryAmountService {

    private final ConsumptionMapper consumptionMapper;

    public CategoryAmountServiceImpl(ConsumptionMapper consumptionMapper) {
        this.consumptionMapper = consumptionMapper;
    }

    @Override
    public List<CategoryAmountDTO> getCategoryAmounts(Integer memberNo, String yearMonth) {
        // 이번 달 소비 내역을 그대로 가져 온다
        List<SpendingVO> spendings = consumptionMapper.selectSpendingByMonth(memberNo, yearMonth);

        // 카테고리별로 내역 금액을 합산
        Map<String, Long> totalsByCategory = new HashMap<>();

        for (SpendingVO spending : spendings) {
            String category = spending.getCategoryName();
            long amount = spending.getAmount();

            // 이 카테고리의 내역을 지금까지 얼마나 더했는지 꺼내서 확인
            long soFar = totalsByCategory.getOrDefault(category, 0L);

            // 지금까지 더한 금액에 이번 내역의 금액을 합산해서 다시 넣는다
            totalsByCategory.put(category, soFar + amount);
        }

        // Map에 담긴 걸 하나씩 꺼내서 DTO 리스트로 옮겨 담는다
        List<CategoryAmountDTO> result = new ArrayList<>();

        for (Map.Entry<String, Long> entry : totalsByCategory.entrySet()) {
            String categoryName = entry.getKey();
            long totalAmount = entry.getValue();

            CategoryAmountDTO dto = CategoryAmountDTO.builder()
                    .categoryName(categoryName)
                    .amount(totalAmount)
                    .build();

            result.add(dto);
        }

        // 금액이 큰 카테고리가 위에 오도록 정렬한다
        result.sort((a, b) -> Long.compare(b.getAmount(), a.getAmount()));

        return result;
    }
}
