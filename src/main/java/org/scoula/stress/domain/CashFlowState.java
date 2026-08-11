package org.scoula.stress.domain;

/**
 * 월 현금흐름 상태
 * 등급이 아니라 수식 분기 그 자체다. 0이라는 자연스러운 수학적 경계이므로 임의 임계값이 없다.
 * @fileName        : CashFlowState
 * @author          : 박상호
 * @since           : 2026-08-11
 */
public enum CashFlowState {

    /** 들어오는 것이 나가는 것보다 많음 */
    SURPLUS,

    /** 수입과 지출이 같음 */
    BALANCED,

    /** 매달 잔액이 줄어듦 */
    DEFICIT,

    /** 소득을 몰라 순부족액을 계산할 수 없음 */
    INCOME_UNKNOWN
}
