package org.scoula.stress.service;

import org.scoula.stress.domain.CashFlowState;
import org.scoula.stress.domain.CoverageStability;
import org.scoula.stress.dto.StressInputDto;
import org.scoula.stress.dto.StressResultDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 스트레스 계산 코어
 * 외부 의존성이 없는 순수 계산 클래스다. DB 도 스프링 컨텍스트도 필요 없으므로
 * 단위 테스트에서 숫자만 넣고 결과를 검증할 수 있다.
 * 즉시 부족액은 일회성 충격에서 잔액을 뺀 값이고, 충격 후 잔액은 잔액에서 일회성 충격을 뺀 값이며,
 * 월 순부족액은 충격 후 지출에서 충격 후 소득을 뺀 값이다.
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

    /**
     * 관측기간 민감도를 판정한다
     * 한 달씩 제외했을 때도 월 부족 상태가 유지되면 기간을 표시할 수 있다.
     * 어떤 달을 빼면 월 여유로 바뀌는 경우 기간이 크게 흔들리므로 앞세우지 않는다.
     * 통계적 추론이 아니라 데이터 구성에 따른 흔들림을 보는 것이다.
     *
     * @param leaveOneOutSpending   한 달씩 제외한 월 환산 지출 목록
     * @param crisisIncome          충격 후 월 소득
     * @param recurringExpenseShock 반복 지출 증가액
     */
    public static CoverageStability judgeStability(List<Long> leaveOneOutSpending,
                                                   Long crisisIncome,
                                                   long recurringExpenseShock) {

        if (leaveOneOutSpending == null || leaveOneOutSpending.isEmpty() || crisisIncome == null) {
            return CoverageStability.NOT_APPLICABLE;
        }

        for (Long spending : leaveOneOutSpending) {
            long gap = (spending + recurringExpenseShock) - crisisIncome;
            if (gap <= 0L) {
                return CoverageStability.SIGN_UNSTABLE;
            }
        }

        return CoverageStability.STABLE;
    }
}