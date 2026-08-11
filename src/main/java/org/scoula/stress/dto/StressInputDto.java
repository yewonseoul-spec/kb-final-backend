package org.scoula.stress.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 스트레스 계산 입력값
 * 월 소득과 잔액은 null 을 허용한다. null 은 0 이 아니라 모른다는 뜻이다.
 * 소득 null 은 사용자가 입력하지 않은 것이고 잔액 null 은 계좌를 연결하지 않은 것이므로
 * 시스템이 이를 0 으로 대체하지 않는다.
 * @fileName        : StressInputDto
 * @author          : 박상호
 * @since           : 2026-08-11
 */
@Getter
public class StressInputDto {

    /** 최근 관측기간 월 환산 지출 */
    private final long monthlySpending;

    /** 등록 월 소득. null 이면 모르는 상태 */
    private final Long monthlyIncome;

    /** 등록 계좌 잔액 합계. null 이면 모르는 상태 */
    private final Long balance;

    /** 반복 지출 증가액 */
    private final long recurringExpenseShock;

    /** 반복 소득 감소액 */
    private final long recurringIncomeShock;

    /** 일회성 충격 금액 */
    private final long oneTimeShock;

    @Builder
    public StressInputDto(long monthlySpending,
                          Long monthlyIncome,
                          Long balance,
                          long recurringExpenseShock,
                          long recurringIncomeShock,
                          long oneTimeShock) {

        validateAmount(monthlySpending, monthlyIncome, recurringExpenseShock,
                recurringIncomeShock, oneTimeShock);

        this.monthlySpending = monthlySpending;
        this.monthlyIncome = monthlyIncome;
        this.balance = balance;
        this.recurringExpenseShock = recurringExpenseShock;
        this.recurringIncomeShock = recurringIncomeShock;
        this.oneTimeShock = oneTimeShock;
    }

    /**
     * 금액 유효 범위를 검증한다
     */
    private static void validateAmount(long monthlySpending,
                                       Long monthlyIncome,
                                       long recurringExpenseShock,
                                       long recurringIncomeShock,
                                       long oneTimeShock) {

        if (monthlySpending < 0) {
            throw new IllegalArgumentException("월 지출은 음수일 수 없습니다: " + monthlySpending);
        }
        if (monthlyIncome != null && monthlyIncome < 0) {
            throw new IllegalArgumentException("월 소득은 음수일 수 없습니다: " + monthlyIncome);
        }
        if (recurringExpenseShock < 0) {
            throw new IllegalArgumentException("지출 증가액은 음수일 수 없습니다: " + recurringExpenseShock);
        }
        if (recurringIncomeShock < 0) {
            throw new IllegalArgumentException("소득 감소액은 음수일 수 없습니다: " + recurringIncomeShock);
        }
        if (oneTimeShock < 0) {
            throw new IllegalArgumentException("일회성 충격 금액은 음수일 수 없습니다: " + oneTimeShock);
        }
        if (monthlyIncome != null && recurringIncomeShock > monthlyIncome) {
            throw new IllegalArgumentException(
                    "소득 감소액이 소득을 초과합니다: " + recurringIncomeShock + " > " + monthlyIncome);
        }
    }

    /**
     * 소득을 모르는 상태인지 확인한다
     */
    public boolean isIncomeUnknown() {
        return monthlyIncome == null;
    }

    /**
     * 잔액을 모르는 상태인지 확인한다
     */
    public boolean isBalanceUnknown() {
        return balance == null;
    }
}