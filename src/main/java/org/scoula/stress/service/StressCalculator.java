package org.scoula.stress.service;

import org.scoula.stress.domain.CashFlowState;
import org.scoula.stress.dto.StressInputDto;
import org.scoula.stress.dto.StressResultDto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 스트레스 계산 코어
 * 외부 의존성이 없는 순수 계산 클래스다. DB 도 스프링 컨텍스트도 필요 없으므로
 * 단위 테스트에서 숫자만 넣고 결과를 검증할 수 있다.
 * 즉시 부족액은 일회성 충격에서 잔액을 뺀 값이고, 충격 후 잔액은 잔액에서 일회성 충격을 뺀 값이며,
 * 월 순부족액은 충격 후 지출에서 충격 후 소득을 뺀 값이다.
 * @fileName        : StressCalculator
 * @author          : 박상호
 * @since           : 2026-08-11
 */
public final class StressCalculator {

    /** 나눗셈 정밀도. 반올림은 최종 표시 단계에서만 하므로 여유 있게 둔다 */
    private static final int DIVISION_SCALE = 4;

    private StressCalculator() {
    }

    /**
     * 스트레스 결과를 계산한다
     */
    public static StressResultDto calculate(StressInputDto stressInput) {

        if (stressInput == null) {
            throw new IllegalArgumentException("입력값이 없습니다");
        }

        // 일회성 충격은 잔액을 아는 경우에만 계산한다
        // 잔액을 모르는데 즉시 부족액이 0 원이라고 출력하면 거짓이 된다
        Long immediateShortfall = null;
        Long postShockBalance = null;

        if (!stressInput.isBalanceUnknown()) {
            long balance = stressInput.getBalance();
            long oneTimeShock = stressInput.getOneTimeShock();
            immediateShortfall = Math.max(oneTimeShock - balance, 0L);
            postShockBalance = Math.max(balance - oneTimeShock, 0L);
        }

        // 소득을 모르면 여기서 멈춘다
        // 사용자가 소득 0 원 가정을 직접 선택하기 전까지 월 순부족액을 만들지 않는다
        if (stressInput.isIncomeUnknown()) {
            return StressResultDto.builder()
                    .state(CashFlowState.INCOME_UNKNOWN)
                    .immediateShortfall(immediateShortfall)
                    .postShockBalance(postShockBalance)
                    .build();
        }

        long crisisSpending = stressInput.getMonthlySpending() + stressInput.getRecurringExpenseShock();
        long crisisIncome = stressInput.getMonthlyIncome() - stressInput.getRecurringIncomeShock();
        long monthlyGap = crisisSpending - crisisIncome;

        if (monthlyGap < 0) {
            return StressResultDto.builder()
                    .state(CashFlowState.SURPLUS)
                    .monthlySurplus(-monthlyGap)
                    .immediateShortfall(immediateShortfall)
                    .postShockBalance(postShockBalance)
                    .build();
        }

        if (monthlyGap == 0) {
            return StressResultDto.builder()
                    .state(CashFlowState.BALANCED)
                    .monthlySurplus(0L)
                    .immediateShortfall(immediateShortfall)
                    .postShockBalance(postShockBalance)
                    .build();
        }

        // 부족 상태이고 잔액을 알 때만 보완 기간을 낸다
        BigDecimal coverageMonths = null;

        if (postShockBalance != null) {
            coverageMonths = BigDecimal.valueOf(postShockBalance)
                    .divide(BigDecimal.valueOf(monthlyGap), DIVISION_SCALE, RoundingMode.HALF_UP);
        }

        return StressResultDto.builder()
                .state(CashFlowState.DEFICIT)
                .monthlyGap(monthlyGap)
                .immediateShortfall(immediateShortfall)
                .postShockBalance(postShockBalance)
                .coverageMonths(coverageMonths)
                .build();
    }
}