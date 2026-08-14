package org.scoula.stress.service;

import org.scoula.stress.domain.StressHorizon;
import org.scoula.stress.domain.StressState;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 스트레스 대응 점수 계산
 * 선택한 충격이 평가 기간 동안 지속된다고 가정했을 때 필요한 총 자금을 구하고,
 * 현재 등록 계좌 잔액이 그중 몇 퍼센트를 충당할 수 있는지를 점수로 만든다.
 *
 * 필요자금 = 일회성 충격 + 월 순부족액 × 평가 기간
 *
 * 한 번 나가는 돈과 매달 나가는 돈을 같은 원 단위로 합치는 것이 핵심이다.
 * 이전에는 기간 비율과 지불 비율을 따로 점수화해 작은 쪽을 골랐는데,
 * 단위가 다른 값을 비교하는 것이라 한쪽이 0 이면 다른 쪽 값이 전부 뭉개졌다.
 */
public final class StressScoreCalculator {

    private StressScoreCalculator() {
    }

    /**
     * 평가 기간 동안 필요한 총 자금을 계산한다
     *
     * @param oneTimeShock 일회성 충격 금액
     * @param monthlyGap   월 순부족액. 음수면 0 으로 본다
     */
    public static long calculateNeed(long oneTimeShock, Long monthlyGap) {
        long gap = (monthlyGap == null || monthlyGap < 0) ? 0L : monthlyGap;
        return oneTimeShock + gap * StressHorizon.MONTHS;
    }

    /**
     * 점수를 계산한다
     * 반올림 전에 전부 충당했는지를 먼저 판정한다.
     * 잔액이 필요자금의 99.5 퍼센트일 때 반올림하면 100 이 되는데
     * 100 점의 정의가 전부 충당 가능이므로 그대로 두면 정의가 깨진다.
     * 1 과 99 는 새 기준이 아니라 0 도 100 도 아니라는 것을 지키기 위한 표시 규칙이다.
     *
     * @param balance 등록 계좌 잔액. 모르면 null
     * @param need    평가 기간 필요자금
     */
    public static Integer calculateScore(Long balance, long need) {

        if (balance == null) {
            return null;
        }
        if (need <= 0L) {
            return 100;
        }
        if (balance >= need) {
            return 100;
        }
        if (balance <= 0L) {
            return 0;
        }

        int raw = BigDecimal.valueOf(balance)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(need), 0, RoundingMode.HALF_UP)
                .intValue();

        return Math.max(1, Math.min(99, raw));
    }

    /**
     * 계산 결과에서 실제로 발생한 상태를 판정한다
     * 점수가 같아도 원인이 다를 수 있으므로 별도로 내려준다.
     *
     * @param score              점수
     * @param baselineGap        충격 전 월 순부족액. 여유면 음수
     * @param stressGap          충격 후 월 순부족액
     * @param immediateShortfall 즉시 부족액
     * @param hasShock           이 조건으로 실제 충격이 걸렸는지
     * @param adjusted           사용자가 지출을 조정했는지
     */
    public static StressState judgeState(Integer score,
                                         Long baselineGap,
                                         Long stressGap,
                                         Long immediateShortfall,
                                         boolean hasShock,
                                         boolean adjusted) {

        if (!hasShock) {
            return StressState.NO_ADDITIONAL_SHOCK;
        }

        boolean hasImmediate = immediateShortfall != null && immediateShortfall > 0;
        boolean hasRecurring = stressGap != null && stressGap > 0;

        // 잔액이 없어 아무것도 감당하지 못하는 경우는 원인을 나눠서 알려준다
        if (score != null && score == 0) {
            if (hasImmediate && hasRecurring) {
                return StressState.IMMEDIATE_AND_RECURRING;
            }
            if (hasImmediate) {
                return StressState.IMMEDIATE_SHORTFALL;
            }
            return StressState.NO_BUFFER_FOR_DEFICIT;
        }

        // 조정으로 월 부족이 사라진 경우
        if (adjusted && !hasRecurring) {
            return StressState.CASHFLOW_RESTORED;
        }

        // 여유였다가 부족으로 뒤집힌 경우. 점수가 100 이어도 알려야 한다
        if (baselineGap != null && baselineGap <= 0 && hasRecurring) {
            return StressState.CASHFLOW_FLIP;
        }

        if (!hasRecurring) {
            return StressState.CASHFLOW_CLEAR;
        }
        if (score != null && score == 100) {
            return StressState.CLEAR;
        }

        return StressState.PARTIAL_COVERAGE;
    }
}