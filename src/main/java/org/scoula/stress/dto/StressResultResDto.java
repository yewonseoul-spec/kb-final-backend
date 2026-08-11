package org.scoula.stress.dto;

import lombok.Builder;
import lombok.Getter;
import org.scoula.stress.domain.CashFlowState;
import org.scoula.stress.domain.DataStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 스트레스 테스트 결과 응답
 * 점수와 등급을 두지 않는다. 외부 비상자금 기준은 잔액을 월 생활비로 나눈 값을 보는데
 * 이 기능의 보완 기간은 잔액을 월 순부족액으로 나눈 값이라 분모가 다르기 때문이다.
 * 대신 버틸 수 있는 기간과 실제 금액을 그대로 보여준다.
 * @fileName        : StressResultResDto
 * @author          : 박상호
 * @since           : 2026-08-12
 */
@Getter
@Builder
public class StressResultResDto {

    /** 소비 데이터 상태 */
    private final DataStatus spendingStatus;

    /** 소득 데이터 상태 */
    private final DataStatus incomeStatus;

    /** 잔액 데이터 상태 */
    private final DataStatus balanceStatus;

    /** 분석 시작월. yyyy-MM */
    private final String analysisStart;

    /** 분석 종료월. yyyy-MM */
    private final String analysisEnd;

    /** 정상 관측된 완결월 수 */
    private final int observationMonths;

    /** 계산 시점 */
    private final LocalDateTime calculatedAt;

    /** 계산에 사용한 월 환산 지출 */
    private final Long monthlySpending;

    /** 계산에 사용한 등록 월 소득 */
    private final Long monthlyIncome;

    /** 계산에 사용한 등록 계좌 잔액 합계 */
    private final Long balance;

    /** 적용한 시나리오 코드 */
    private final String scenarioCode;

    /** 적용한 시나리오명 */
    private final String scenarioName;

    /** 적용 내역 문구 */
    private final String appliedDescription;

    /** 월 현금흐름 상태 */
    private final CashFlowState cashFlowState;

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

    /** 카테고리별 지출 구성. 총액 내림차순 */
    private final List<CategorySummaryResDto> categories;

    /** 계산 근거 */
    private final List<String> basis;

    /** 이 계산에 적용된 한계 */
    private final List<String> limitations;
}