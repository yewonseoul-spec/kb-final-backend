package org.scoula.stress.dto;

import lombok.Data;

// stress-02: 카테고리별 월평균 지출 (MyBatis resultType이라 setter 필요)
@Data
public class CategorySpendingDto {
    private String categoryName;
    private Long monthlyAmount;
    private Integer monthCount;   // 집계에 사용된 개월 수. 근거 표시에 쓴다
}