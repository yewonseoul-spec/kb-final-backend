package org.scoula.stress.dto;

import lombok.Builder;
import lombok.Getter;
import org.scoula.stress.domain.CashFlowState;

import java.math.BigDecimal;

/**
 * 스트레스 계산 결과
 * null 은 계산하지 않았다는 뜻이며 0 으로 대체하지 않는다.
 * 잔액을 모르면 즉시 부족액과 충격 후 잔액을, 소득을 모르면 월 부족액과 보완 기간을 만들지 않는다.
 * @fileName        : StressResultDto
 * @author          : 박상호
 * @since           : 2026-08-11
 */
@Getter
@Builder
public class StressResultDto {

    /** 월 현금흐름 상태 */
    private final CashFlowState state;

    /** 월 순부족액. 부족 상태일 때만 값이 있다 */
    private final Long monthlyGap;

    /** 월 여유액. 여유 또는 균형 상태일 때만 값이 있다 */
    private final Long monthlySurplus;

    /** 즉시 부족액. 잔액을 알 때만 값이 있다 */
    private final Long immediateShortfall;

    /** 충격 후 잔액. 잔액을 알 때만 값이 있다 */
    private final Long postShockBalance;

    /** 월 부족액 보완 가능 기간. 부족 상태이고 잔액을 알 때만 값이 있다 */
    private final BigDecimal coverageMonths;
}