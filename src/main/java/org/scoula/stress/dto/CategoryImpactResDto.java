package org.scoula.stress.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 카테고리별 충격 영향
 * 충격이 어느 카테고리에 얼마나 걸렸는지를 순차로 보여주기 위한 값이다.
 * 총액만 내려주면 어디서 늘었는지 알 수 없어 변화 과정을 표현할 수 없다.
 */
@Getter
@Builder
public class CategoryImpactResDto {

    /** 카테고리명 */
    private final String categoryName;

    /** 충격 전 월 환산 금액 */
    private final long beforeAmount;

    /** 충격 후 월 환산 금액 */
    private final long afterAmount;

    /** 증가액 */
    private final long impact;
}
