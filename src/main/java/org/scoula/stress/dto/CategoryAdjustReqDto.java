package org.scoula.stress.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 카테고리별 지출 조정 요청
 * 사용자가 직접 고른 감소율이다. 시스템이 줄일 수 있다고 판단한 값이 아니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class CategoryAdjustReqDto {

    /** 카테고리명 */
    private String categoryName;

    /** 감소율. 0 이상 1 이하 */
    private BigDecimal reductionRate;
}