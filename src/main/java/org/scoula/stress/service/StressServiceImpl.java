package org.scoula.stress.service;

import lombok.RequiredArgsConstructor;
import org.scoula.stress.domain.AnalysisWindow;
import org.scoula.stress.domain.CoverageStability;
import org.scoula.stress.domain.DataStatus;
import org.scoula.stress.domain.ShockTarget;
import org.scoula.stress.domain.StressScenarioVO;
import org.scoula.stress.domain.StressState;
import org.scoula.stress.dto.CategoryImpactResDto;
import org.scoula.stress.dto.CategoryMonthlyResDto;
import org.scoula.stress.dto.CategorySummaryResDto;
import org.scoula.stress.dto.RebalanceOptionResDto;
import org.scoula.stress.dto.ScenarioShockDto;
import org.scoula.stress.dto.SpendingSummaryResDto;
import org.scoula.stress.dto.StressInputDto;
import org.scoula.stress.dto.StressLevelResDto;
import org.scoula.stress.dto.StressResultDto;
import org.scoula.stress.dto.StressResultReqDto;
import org.scoula.stress.dto.StressResultResDto;
import org.scoula.stress.dto.StressScenarioResDto;
import org.scoula.stress.mapper.StressMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 스트레스 테스트 서비스
 * 계산은 StressCalculator 와 SpendingAggregator 와 StressScoreCalculator 가 하고
 * 이 클래스는 데이터를 모아 넘기고 응답을 조립한다.
 *
 * 세계와 강도의 정의는 화면이 가지고 있고 서버는 충격 값만 받는다.
 * 세계를 늘리거나 강도를 바꿀 때 DB 를 건드리지 않기 위해서다.
 *
 * 모르는 값은 0 으로 대체하지 않고 상태로 내려준다.
 */
@Service
@RequiredArgsConstructor
public class StressServiceImpl implements StressService {

    private final StressMapper stressMapper;

    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 최초 거래월을 부분 월로 보고 제외할지 여부 */
    private static final boolean SKIP_FIRST_SPENDING_MONTH = false;

    /** 나눗셈 정밀도 */
    private static final int DIVISION_SCALE = 4;

    /** 시나리오 강도 표시 문구 */
    private static final Map<String, String> LEVEL_LABEL = new LinkedHashMap<String, String>() {{
        put("LOW", "낮음");
        put("MID", "보통");
        put("HIGH", "높음");
    }};

    @Override
    public List<StressScenarioResDto> findScenarios() {

        List<StressScenarioVO> rows = stressMapper.findAllScenarios();

        // 조회 쿼리의 정렬 순서를 유지해야 하므로 LinkedHashMap 을 쓴다
        Map<String, List<StressScenarioVO>> grouped = new LinkedHashMap<>();
        for (StressScenarioVO row : rows) {
            grouped.computeIfAbsent(row.getScenarioCode(), key -> new ArrayList<>()).add(row);
        }

        List<StressScenarioResDto> result = new ArrayList<>();

        for (List<StressScenarioVO> levels : grouped.values()) {
            StressScenarioVO head = levels.get(0);

            List<StressLevelResDto> levelDtos = new ArrayList<>();
            for (StressScenarioVO row : levels) {
                levelDtos.add(StressLevelResDto.builder()
                        .scenarioNo(row.getScenarioNo())
                        .shockLevel(row.getShockLevel())
                        .label(LEVEL_LABEL.getOrDefault(row.getShockLevel(), row.getShockLevel()))
                        .changeRate(row.getChangeRate())
                        .fixedAmount(row.getFixedAmount())
                        .displayText(createDisplayText(row))
                        .build());
            }

            result.add(StressScenarioResDto.builder()
                    .scenarioCode(head.getScenarioCode())
                    .scenarioName(head.getScenarioName())
                    .description(head.getDescription())
                    .targetCategory(head.getTargetCategory())
                    .available(true)
                    .levels(levelDtos)
                    .build());
        }

        return result;
    }

    @Override
    public StressResultResDto calculate(StressResultReqDto req) {

        int memberNo = req.getMemberNo();

        // 분석 창을 만든다. 당월은 온전한 한 달이 아니므로 제외한다
        AnalysisWindow window = createWindow(memberNo);

        if (window.isEmpty()) {
            return createInsufficientHistoryResult();
        }

        // 소비를 집계한다. 분모는 정상 관측 완결월 수다
        List<CategoryMonthlyResDto> monthlyRows = stressMapper.findCategoryMonthlySpending(
                memberNo,
                window.getStartDateInclusive().toString(),
                window.getEndDateExclusive().toString());

        SpendingSummaryResDto spendingSummary = SpendingAggregator.aggregate(window, monthlyRows);

        // 소득과 잔액을 확인한다. 없으면 0 이 아니라 모르는 상태다
        Long monthlyIncome = stressMapper.findMonthlyIncome(memberNo);
        int accountCount = stressMapper.findAccountCount(memberNo);
        Long balance = accountCount == 0 ? null : stressMapper.findTotalBalance(memberNo);

        // 화면이 보낸 충격 값을 계산 입력으로 바꾼다
        ScenarioShockDto shock = resolveShock(req, spendingSummary, monthlyIncome);

        // 사용자가 고른 감소율을 반영한다. 충격이 적용된 뒤의 금액에서 줄인다
        long adjustedReduction = AdjustmentResolver.resolveReduction(
                spendingSummary, shock, req.getAdjustments());

        // 조정 전 결과. 얼마나 회복했는지 보여주기 위한 비교 기준이다
        StressResultDto beforeAdjust = StressCalculator.calculate(StressInputDto.builder()
                .monthlySpending(spendingSummary.getMonthlySpending())
                .monthlyIncome(monthlyIncome)
                .balance(balance)
                .recurringExpenseShock(shock.getRecurringExpenseShock())
                .recurringIncomeShock(shock.getRecurringIncomeShock())
                .oneTimeShock(shock.getOneTimeShock())
                .build());

        // 조정을 반영해 계산한다. 감소액이 0 이면 조정 전과 같다
        long adjustedSpending = Math.max(
                spendingSummary.getMonthlySpending() + shock.getRecurringExpenseShock() - adjustedReduction,
                0L);

        StressResultDto calculated = StressCalculator.calculate(StressInputDto.builder()
                .monthlySpending(adjustedSpending)
                .monthlyIncome(monthlyIncome)
                .balance(balance)
                .recurringExpenseShock(0L)
                .recurringIncomeShock(shock.getRecurringIncomeShock())
                .oneTimeShock(shock.getOneTimeShock())
                .build());

        // 충격을 걸지 않았을 때의 결과. 변화를 보여주기 위한 비교 기준이다
        StressResultDto baseline = StressCalculator.calculate(StressInputDto.builder()
                .monthlySpending(spendingSummary.getMonthlySpending())
                .monthlyIncome(monthlyIncome)
                .balance(balance)
                .build());

        // 평가 기간 필요자금과 점수를 구한다
        // 한 번 나가는 돈과 매달 나가는 돈을 같은 원 단위로 합쳐 잔액과 비교한다
        long stressNeed = StressScoreCalculator.calculateNeed(
                shock.getOneTimeShock(), calculated.getMonthlyGap());

        Integer score = StressScoreCalculator.calculateScore(balance, stressNeed);

        // 충격 전 월 순부족액. 여유 상태면 음수로 만들어 부호 전환을 판정할 수 있게 한다
        Long baselineGap = baseline.getMonthlyGap() != null
                ? baseline.getMonthlyGap()
                : (baseline.getMonthlySurplus() != null ? -baseline.getMonthlySurplus() : null);

        boolean hasShock = shock.getRecurringExpenseShock() > 0
                || shock.getRecurringIncomeShock() > 0
                || shock.getOneTimeShock() > 0;

        StressState state = StressScoreCalculator.judgeState(
                score, baselineGap, calculated.getMonthlyGap(),
                calculated.getImmediateShortfall(), hasShock, adjustedReduction > 0);

        // 관측한 달을 하나씩 빼봤을 때도 월 부족 상태가 유지되는지 확인한다
        CoverageStability stability = CoverageStability.NOT_APPLICABLE;
        if (calculated.getCoverageMonths() != null && monthlyIncome != null) {
            long crisisIncome = monthlyIncome - shock.getRecurringIncomeShock();
            stability = StressCalculator.judgeStability(
                    spendingSummary.getLeaveOneOutSpending(),
                    crisisIncome,
                    shock.getRecurringExpenseShock() - adjustedReduction);
        }

        return StressResultResDto.builder()
                .spendingStatus(DataStatus.KNOWN)
                .incomeStatus(monthlyIncome == null
                        ? DataStatus.NEEDS_USER_ASSUMPTION : DataStatus.KNOWN)
                .balanceStatus(accountCount == 0 ? DataStatus.NO_ACCOUNT : DataStatus.KNOWN)
                .analysisStart(spendingSummary.getStartMonth().format(YEAR_MONTH_FORMAT))
                .analysisEnd(spendingSummary.getEndMonth().format(YEAR_MONTH_FORMAT))
                .observationMonths(spendingSummary.getObservationMonths())
                .calculatedAt(LocalDateTime.now(AnalysisWindow.SERVICE_ZONE))
                .monthlySpending(spendingSummary.getMonthlySpending())
                .monthlyIncome(monthlyIncome)
                .balance(balance)
                .scenarioCode(req.getWorldCode())
                .scenarioName(req.getWorldCode())
                .appliedDescription(shock.getAppliedDescription())
                .cashFlowState(calculated.getState())
                .monthlyGap(calculated.getMonthlyGap())
                .monthlySurplus(calculated.getMonthlySurplus())
                .immediateShortfall(calculated.getImmediateShortfall())
                .postShockBalance(calculated.getPostShockBalance())
                .coverageMonths(calculated.getCoverageMonths())
                .coverageStability(stability)
                .score(score)
                .stressNeed(stressNeed)
                .state(state)
                .baselineCashFlowState(baseline.getState())
                .baselineGap(baselineGap)
                .categories(spendingSummary.getCategories())
                .basis(createBasis(spendingSummary, monthlyIncome, balance, shock,
                        adjustedReduction, stressNeed))
                .limitations(createLimitations(spendingSummary, monthlyIncome, accountCount))
                .baselineCoverageMonths(baseline.getCoverageMonths())
                .categoryImpacts(shock.getCategoryImpacts())
                .rebalanceOptions(createRebalanceOptions(spendingSummary, calculated))
                .gapBeforeAdjust(adjustedReduction > 0 ? beforeAdjust.getMonthlyGap() : null)
                .adjustedReduction(adjustedReduction)
                .coverageMonthsBeforeAdjust(
                        adjustedReduction > 0 ? beforeAdjust.getCoverageMonths() : null)
                .build();
    }

    /**
     * 분석 창을 만든다
     * 소비 데이터가 처음 나타난 달 이전은 0 원이 아니라 모르는 기간이므로 창에서 뺀다.
     */
    private AnalysisWindow createWindow(int memberNo) {
        String firstMonth = stressMapper.findFirstSpendingMonth(memberNo);
        YearMonth firstSpendingMonth = firstMonth == null
                ? null : YearMonth.parse(firstMonth, YEAR_MONTH_FORMAT);

        return AnalysisWindow.createWindow(
                LocalDate.now(AnalysisWindow.SERVICE_ZONE),
                firstSpendingMonth,
                SKIP_FIRST_SPENDING_MONTH);
    }

    /**
     * 화면이 보낸 충격 값을 계산 입력으로 바꾼다
     * 비율은 생활밀접 지출과 등록 소득에만 곱하고 정액은 그대로 더한다.
     * 값의 범위는 서버가 검증한다.
     */
    private ScenarioShockDto resolveShock(StressResultReqDto req,
                                          SpendingSummaryResDto spendingSummary,
                                          Long monthlyIncome) {

        BigDecimal expenseRate = normalizeRate(req.getExpenseRate(), "지출 증가 비율");
        BigDecimal incomeRate = normalizeRate(req.getIncomeRate(), "소득 감소 비율");
        long fixedExpense = req.getFixedExpense() == null ? 0L : req.getFixedExpense();
        long oneTime = req.getOneTimeAmount() == null ? 0L : req.getOneTimeAmount();

        if (fixedExpense < 0 || oneTime < 0) {
            throw new IllegalArgumentException("충격 금액은 음수일 수 없습니다");
        }

        // 생활밀접 카테고리에만 비율을 적용한다. 카테고리마다 얼마나 늘었는지 함께 남긴다
        List<CategoryImpactResDto> impacts = new ArrayList<>();
        long expenseShock = 0L;

        if (expenseRate.compareTo(BigDecimal.ZERO) > 0) {
            for (CategorySummaryResDto category : spendingSummary.getCategories()) {
                if (!ShockTarget.isLivingCost(category.getCategoryName())
                        || category.getMonthlyAverage() <= 0L) {
                    continue;
                }
                long impact = BigDecimal.valueOf(category.getMonthlyAverage())
                        .multiply(expenseRate)
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValue();
                if (impact <= 0L) {
                    continue;
                }
                impacts.add(CategoryImpactResDto.builder()
                        .categoryName(category.getCategoryName())
                        .beforeAmount(category.getMonthlyAverage())
                        .afterAmount(category.getMonthlyAverage() + impact)
                        .impact(impact)
                        .build());
                expenseShock += impact;
            }
            impacts.sort((a, b) -> Long.compare(b.getImpact(), a.getImpact()));
        }

        expenseShock += fixedExpense;

        // 소득을 모르면 감소액을 만들 수 없다
        long incomeShock = 0L;
        if (monthlyIncome != null && incomeRate.compareTo(BigDecimal.ZERO) > 0) {
            incomeShock = BigDecimal.valueOf(monthlyIncome)
                    .multiply(incomeRate)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
        }

        return ScenarioShockDto.builder()
                .recurringExpenseShock(expenseShock)
                .recurringIncomeShock(incomeShock)
                .oneTimeShock(oneTime)
                .appliedDescription(req.getStageLabel() == null ? "평상시" : req.getStageLabel())
                .categoryImpacts(impacts)
                .build();
    }

    /**
     * 비율 값을 확인한다. 없으면 0 으로 본다
     */
    private BigDecimal normalizeRate(BigDecimal rate, String name) {
        if (rate == null) {
            return BigDecimal.ZERO;
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(name + "은 0 과 1 사이여야 합니다: " + rate);
        }
        return rate;
    }

    /**
     * 지출 조정 선택지를 만든다
     * 각 카테고리를 전액 조정한다고 가정했을 때 늘어나는 기간을 미리 계산한다.
     * 시스템이 줄일 수 있다고 판단하는 것이 아니라 가정했을 때의 계산값이다.
     */
    private List<RebalanceOptionResDto> createRebalanceOptions(SpendingSummaryResDto spendingSummary,
                                                               StressResultDto current) {

        List<RebalanceOptionResDto> options = new ArrayList<>();

        // 부족 상태가 아니거나 잔액을 모르면 기간 변화를 계산할 수 없다
        boolean calculable = current.getMonthlyGap() != null
                && current.getMonthlyGap() > 0
                && current.getPostShockBalance() != null;

        long currentGap = calculable ? current.getMonthlyGap() : 0L;
        long balance = calculable ? current.getPostShockBalance() : 0L;
        BigDecimal currentMonths = calculable ? current.getCoverageMonths() : null;

        for (CategorySummaryResDto category : spendingSummary.getCategories()) {
            if (category.getMonthlyAverage() <= 0L) {
                continue;
            }

            BigDecimal gainMonths = null;
            boolean resolvesGap = false;

            if (calculable) {
                long nextGap = currentGap - category.getMonthlyAverage();
                if (nextGap <= 0L) {
                    resolvesGap = true;
                } else {
                    BigDecimal nextMonths = BigDecimal.valueOf(balance)
                            .divide(BigDecimal.valueOf(nextGap), DIVISION_SCALE, RoundingMode.HALF_UP);
                    gainMonths = nextMonths.subtract(currentMonths);
                }
            }

            options.add(RebalanceOptionResDto.builder()
                    .categoryName(category.getCategoryName())
                    .monthlyAmount(category.getMonthlyAverage())
                    .gainMonths(gainMonths)
                    .resolvesGap(resolvesGap)
                    .build());
        }

        return options;
    }

    /**
     * 관측 완결월이 없을 때의 응답을 만든다
     */
    private StressResultResDto createInsufficientHistoryResult() {
        return StressResultResDto.builder()
                .spendingStatus(DataStatus.INSUFFICIENT_HISTORY)
                .incomeStatus(DataStatus.UNKNOWN)
                .balanceStatus(DataStatus.UNKNOWN)
                .observationMonths(0)
                .calculatedAt(LocalDateTime.now(AnalysisWindow.SERVICE_ZONE))
                .coverageStability(CoverageStability.NOT_APPLICABLE)
                .state(StressState.NO_ADDITIONAL_SHOCK)
                .stressNeed(0L)
                .categories(new ArrayList<>())
                .basis(new ArrayList<>())
                .limitations(Arrays.asList("분석할 수 있는 완결월이 없어 계산하지 못했습니다."))
                .categoryImpacts(new ArrayList<>())
                .rebalanceOptions(new ArrayList<>())
                .adjustedReduction(0L)
                .build();
    }

    /**
     * 계산 근거를 만든다
     * 누구나 손으로 다시 계산해서 같은 답이 나와야 한다.
     */
    private List<String> createBasis(SpendingSummaryResDto spendingSummary,
                                     Long monthlyIncome,
                                     Long balance,
                                     ScenarioShockDto shock,
                                     long adjustedReduction,
                                     long stressNeed) {

        List<String> basis = new ArrayList<>();

        basis.add(String.format("최근 관측기간 월 환산 지출 %,d원 (%d개 완결월 기준)",
                spendingSummary.getMonthlySpending(), spendingSummary.getObservationMonths()));

        basis.add(monthlyIncome == null
                ? "등록 월소득 정보가 없습니다"
                : String.format("등록 월소득 %,d원", monthlyIncome));

        basis.add(balance == null
                ? "등록된 계좌가 없어 잔액을 알 수 없습니다"
                : String.format("등록 계좌 잔액 합계 %,d원", balance));

        basis.add("적용한 가정 " + shock.getAppliedDescription());

        if (shock.getRecurringExpenseShock() > 0) {
            basis.add(String.format("월 지출 증가 %,d원", shock.getRecurringExpenseShock()));
        }
        if (shock.getRecurringIncomeShock() > 0) {
            basis.add(String.format("월 소득 감소 %,d원", shock.getRecurringIncomeShock()));
        }
        if (shock.getOneTimeShock() > 0) {
            basis.add(String.format("일회성 비용 %,d원", shock.getOneTimeShock()));
        }
        if (adjustedReduction > 0) {
            basis.add(String.format("내가 조정한 지출 감소 %,d원", adjustedReduction));
        }

        basis.add(String.format("6개월 필요자금 %,d원", stressNeed));

        return basis;
    }

    /**
     * 이 계산에 적용된 주의사항을 만든다
     * 숨기면 신뢰가 깨지므로 화면에 그대로 내려준다.
     */
    private List<String> createLimitations(SpendingSummaryResDto spendingSummary,
                                           Long monthlyIncome,
                                           int accountCount) {

        List<String> limitations = new ArrayList<>();

        if (spendingSummary.getObservationMonths() < 3) {
            limitations.add("관측 기간이 짧아 특정 월의 소비가 월 환산값에 크게 반영될 수 있습니다.");
        }
        if (monthlyIncome == null) {
            limitations.add("등록 월소득이 없어 월 부족액을 계산하지 않았습니다.");
        }
        if (accountCount == 0) {
            limitations.add("등록된 계좌가 없어 잔액 기반 결과를 계산하지 않았습니다.");
        }

        limitations.add("입출금 계좌 잔액만 사용하며 예적금 같은 금융상품은 포함하지 않습니다.");
        limitations.add("현재의 지출과 소득 수준이 유지된다는 가정 아래 계산했습니다.");

        return limitations;
    }

    /**
     * 강도 표시 문구를 만든다
     */
    private String createDisplayText(StressScenarioVO scenario) {
        if (scenario.getFixedAmount() != null) {
            return String.format("%s +%,d원",
                    scenario.getTargetCategory() == null ? "추가 비용" : scenario.getTargetCategory(),
                    scenario.getFixedAmount());
        }
        if (scenario.getChangeRate() != null) {
            return String.format("%s +%s%%",
                    scenario.getTargetCategory() == null ? "지출" : scenario.getTargetCategory(),
                    scenario.getChangeRate()
                            .multiply(BigDecimal.valueOf(100))
                            .stripTrailingZeros().toPlainString());
        }
        return "";
    }
}