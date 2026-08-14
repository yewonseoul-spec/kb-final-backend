package org.scoula.stress.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 지출 조정 선택지
 * 이 카테고리를 전액 조정한다고 가정했을 때 버틸 수 있는 기간이 얼마나 늘어나는지를
 * 미리 계산해 내려준다. 고르기 전에 효과가 보여야 사용자가 비교할 수 있다.
 * 시스템이 줄일 수 있다고 판단하는 것이 아니라 가정했을 때의 계산값이다.
 */
@Getter
@Builder
public class RebalanceOptionResDto {

    /** 카테고리명 */
    private final String categoryName;

    /** 월 환산 금액 */
    private final long monthlyAmount;

    /** 전액 조정 가정 시 늘어나는 기간. 부족 상태이고 잔액을 알 때만 값이 있다 */
    private final BigDecimal gainMonths;

    /** 이 항목만으로 월 부족액이 사라지는지 여부 */
    private final boolean resolvesGap;
}