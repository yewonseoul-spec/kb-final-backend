package org.scoula.stress.domain;

/**
 * 계산 과정에서 실제로 발생한 상태
 * 금융 등급이 아니라 어떤 사건이 일어났는지를 설명하는 코드다.
 * 같은 점수라도 원인이 다를 수 있으므로 반드시 구분한다.
 */
public enum StressState {

    /** 평가 기간 필요자금을 전부 충당했다 */
    CLEAR,

    /** 충격 후에도 월 부족이 발생하지 않았다 */
    CASHFLOW_CLEAR,

    /** 월 여유였다가 월 부족으로 뒤집혔다 */
    CASHFLOW_FLIP,

    /** 일회성 비용을 잔액으로 즉시 감당하지 못한다 */
    IMMEDIATE_SHORTFALL,

    /** 월 부족이 생겼지만 보완할 잔액이 없다 */
    NO_BUFFER_FOR_DEFICIT,

    /** 즉시 부족과 월 부족이 함께 발생했다 */
    IMMEDIATE_AND_RECURRING,

    /** 이 조건으로 추가되는 충격이 없다 */
    NO_ADDITIONAL_SHOCK,

    /** 지출 조정으로 월 부족이 사라졌다 */
    CASHFLOW_RESTORED,

    /** 필요자금 일부만 충당한 일반 상태 */
    PARTIAL_COVERAGE
}
