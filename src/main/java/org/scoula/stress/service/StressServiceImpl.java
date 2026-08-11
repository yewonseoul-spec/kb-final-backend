package org.scoula.stress.service;

import lombok.RequiredArgsConstructor;
import org.scoula.stress.domain.AnalysisWindow;
import org.scoula.stress.domain.DataStatus;
import org.scoula.stress.domain.StressScenarioVO;
import org.scoula.stress.dto.CategoryMonthlyResDto;
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
 * 계산은 StressCalculator 와 SpendingAggregator 가 하고 이 클래스는 데이터를 모아 넘긴다.
 * 점수와 등급을 만들지 않는다. 외부 비상자금 기준은 잔액을 월 생활비로 나눈 값을 보는데
 * 이 기능의 보완 기간은 잔액을 월 순부족액으로 나눈 값이라 분모가 다르기 때문이다.
 * 모르는 값은 0 으로 대체하지 않고 상태로 내려준다.
 * @fileName        : StressServiceImpl
 * @author          : 박상호
 * @since           : 2026-08-12
 */
@Service
@RequiredArgsConstructor
public class StressServiceImpl implements StressService {

    private final StressMapper stressMapper;

    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final String CODE_COMPLEX = "COMPLEX";
    private static final String CODE_JOB_LOSS = "JOB_LOSS";
    private static final String CODE_RATE = "RATE";

    private static final String UNAVAILABLE_REASON =
            "충격 강도 기준이 아직 정해지지 않아 계산할 수 없습니다.";

    /** 최초 거래월을 부분 월로 보고 제외할지 여부. 실측 후 조정한다 */
    private static final boolean SKIP_FIRST_SPENDING_MONTH = false;

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

            // 금리 시나리오는 대출 데이터가 자산과 정책과 신청 이력 어디에도 없어 제외한다
            if (CODE_RATE.equals(head.getScenarioCode())) {
                continue;
            }

            boolean available = CODE_COMPLEX.equals(head.getScenarioCode())
                    || levels.stream().anyMatch(
                    row -> row.getChangeRate() != null || row.getFixedAmount() != null);

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
                    .available(available)
                    .unavailableReason(available ? null : UNAVAILABLE_REASON)
                    .levels(levelDtos)
                    .build());
        }

        return result;
    }

    @Override
    public StressResultResDto calculate(StressResultReqDto req) {

        int memberNo = req.getMemberNo();

        // 1. 분석 창을 만든다. 당월은 온전한 한 달이 아니므로 제외한다
        AnalysisWindow window = createWindow(memberNo);

        if (window.isEmpty()) {
            return createInsufficientHistoryResult();
        }

        // 2. 소비를 집계한다. 분모는 정상 관측 완결월 수다
        List<CategoryMonthlyResDto> monthlyRows = stressMapper.findCategoryMonthlySpending(
                memberNo,
                window.getStartDateInclusive().toString(),
                window.getEndDateExclusive().toString());

        SpendingSummaryResDto spendingSummary = SpendingAggregator.aggregate(window, monthlyRows);

        // 3. 소득과 잔액을 확인한다. 없으면 0 이 아니라 모르는 상태다
        Long monthlyIncome = stressMapper.findMonthlyIncome(memberNo);
        int accountCount = stressMapper.findAccountCount(memberNo);
        Long balance = accountCount == 0 ? null : stressMapper.findTotalBalance(memberNo);

        // 4. 시나리오를 충격으로 변환한다
        ScenarioShockDto shock = resolveShock(req, spendingSummary, monthlyIncome);

        // 5. 계산한다
        StressResultDto calculated = StressCalculator.calculate(StressInputDto.builder()
                .monthlySpending(spendingSummary.getMonthlySpending())
                .monthlyIncome(monthlyIncome)
                .balance(balance)
                .recurringExpenseShock(shock.getRecurringExpenseShock())
                .recurringIncomeShock(shock.getRecurringIncomeShock())
                .oneTimeShock(shock.getOneTimeShock())
                .build());

        // 6. 응답을 만든다
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
                .scenarioCode(req.getScenarioCode())
                .scenarioName(findScenarioName(req.getScenarioCode()))
                .appliedDescription(shock.getAppliedDescription())
                .cashFlowState(calculated.getState())
                .monthlyGap(calculated.getMonthlyGap())
                .monthlySurplus(calculated.getMonthlySurplus())
                .immediateShortfall(calculated.getImmediateShortfall())
                .postShockBalance(calculated.getPostShockBalance())
                .coverageMonths(calculated.getCoverageMonths())
                .categories(spendingSummary.getCategories())
                .basis(createBasis(spendingSummary, monthlyIncome, balance, shock))
                .limitations(createLimitations(spendingSummary, monthlyIncome, accountCount))
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
                java.time.LocalDate.now(AnalysisWindow.SERVICE_ZONE),
                firstSpendingMonth,
                SKIP_FIRST_SPENDING_MONTH);
    }

    /**
     * 요청 시나리오를 충격으로 변환한다
     * 복합 시나리오는 서로 다른 축을 함께 선택한 상태이므로 개별 시나리오를 모아 넘긴다.
     */
    private ScenarioShockDto resolveShock(StressResultReqDto req,
                                          SpendingSummaryResDto spendingSummary,
                                          Long monthlyIncome) {

        String scenarioCode = req.getScenarioCode();

        if (scenarioCode == null) {
            return ScenarioShockDto.none();
        }

        // 소득 전액 상실은 stress_scenario 에 없으므로 코드에서 처리한다
        if (CODE_JOB_LOSS.equals(scenarioCode)) {
            long incomeShock = monthlyIncome == null ? 0L : monthlyIncome;
            return ScenarioShockDto.builder()
                    .recurringExpenseShock(0L)
                    .recurringIncomeShock(incomeShock)
                    .oneTimeShock(0L)
                    .appliedDescription("등록 월소득 전액 상실")
                    .build();
        }

        if (CODE_COMPLEX.equals(scenarioCode)) {
            List<StressScenarioVO> scenarios =
                    stressMapper.findScenariosByLevel(req.getShockLevel());
            ScenarioShockDto expenseShock =
                    ScenarioShockResolver.resolve(scenarios, spendingSummary);

            // 복합 시나리오에서는 소득 충격도 함께 적용한다
            long incomeShock = monthlyIncome == null ? 0L : monthlyIncome;
            return ScenarioShockDto.builder()
                    .recurringExpenseShock(expenseShock.getRecurringExpenseShock())
                    .recurringIncomeShock(incomeShock)
                    .oneTimeShock(expenseShock.getOneTimeShock())
                    .appliedDescription(expenseShock.getAppliedDescription()
                            + " · 등록 월소득 전액 상실")
                    .build();
        }

        StressScenarioVO scenario =
                stressMapper.findScenario(scenarioCode, req.getShockLevel());

        if (scenario == null) {
            return ScenarioShockDto.none();
        }

        return ScenarioShockResolver.resolve(Arrays.asList(scenario), spendingSummary);
    }

    /**
     * 시나리오명을 조회한다
     */
    private String findScenarioName(String scenarioCode) {
        if (CODE_JOB_LOSS.equals(scenarioCode)) {
            return "소득이 끊긴 세계";
        }
        List<StressScenarioVO> rows = stressMapper.findAllScenarios();
        for (StressScenarioVO row : rows) {
            if (row.getScenarioCode().equals(scenarioCode)) {
                return row.getScenarioName();
            }
        }
        return scenarioCode;
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
                .categories(new ArrayList<>())
                .basis(new ArrayList<>())
                .limitations(Arrays.asList(
                        "분석할 수 있는 완결월이 없어 계산하지 못했습니다."))
                .build();
    }

    /**
     * 계산 근거를 만든다
     * 누구나 손으로 다시 계산해서 같은 답이 나와야 한다.
     */
    private List<String> createBasis(SpendingSummaryResDto spendingSummary,
                                     Long monthlyIncome,
                                     Long balance,
                                     ScenarioShockDto shock) {

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

        return basis;
    }

    /**
     * 이 계산에 적용된 한계를 만든다
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

        limitations.add("계좌 유형을 구분할 수 없어 실제 즉시 사용 가능한 자금과 다를 수 있습니다.");
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
                            .multiply(java.math.BigDecimal.valueOf(100))
                            .stripTrailingZeros().toPlainString());
        }
        return "";
    }
}