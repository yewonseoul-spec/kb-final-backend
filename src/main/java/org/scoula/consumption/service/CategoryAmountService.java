package org.scoula.consumption.service;

import org.scoula.consumption.dto.CategoryAmountDTO;

import java.util.List;

public interface CategoryAmountService {
    // 이번 달 소비 내역을 카테고리별로 합산하여 금액이 큰 순서대로 돌려준다
    List<CategoryAmountDTO> getCategoryAmounts(Integer memberNo, String yearMonth);
}
