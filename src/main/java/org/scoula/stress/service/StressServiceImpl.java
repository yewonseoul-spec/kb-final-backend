package org.scoula.stress.service;

import lombok.RequiredArgsConstructor;
import org.scoula.stress.domain.StressScenarioVO;
import org.scoula.stress.dto.CategorySpendingDto;
import org.scoula.stress.dto.StressBreakdownDto;
import org.scoula.stress.dto.StressLevelResDto;
import org.scoula.stress.dto.StressReductionDto;
import org.scoula.stress.dto.StressResultReqDto;
import org.scoula.stress.dto.StressResultResDto;
import org.scoula.stress.dto.StressScenarioResDto;
import org.scoula.stress.mapper.StressMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StressServiceImpl implements StressService {

    private final StressMapper stressMapper;

    private static final String UNAVAILABLE_REASON =
            "충격 강도 기준이 아직 정해지지 않아 계산할 수 없습니다.";

    // 방어력 점수 만점 기준. 권장 비상자금 6개월치를 100점으로 환산한다.
    private static final double SCORE_BASE_MONTHS = 6.0;

    /**
     * 위기 시 카테고리별 유지율.
     *
     * 룰북은 9종 분류를 쓰지만 DB 소비 카테고리 15종과 대응이 맞지 않는다.
     * ('구독'과 '금융·보험'은 DB에 없고, '마트·편의점'은 9종 어디에도 속하지 않는다)
     * 그래서 중간 분류를 없애고 15종에 직접 유지율을 부여했다. 수치는 룰북을 따랐고,
     * 룰북에 없던 교육·여행·경조사만 같은 기준(계약인가 / 대체 수단이 있나 / 안 써도 사는가)으로 정했다.
     */
    private static final Map<String, Double> KEEP_RATE = new LinkedHashMap<String, Double>() {{
        put("주거·공과금", 1.00);   // 계약 고정
        put("통신", 0.85);          // 최저 요금제 전환
        put("교통", 0.65);          // 대중교통 전환
        put("식비", 0.60);          // 외식 줄임
        put("마트·편의점", 0.60);   // 필수 장보기는 유지
        put("생활", 0.50);          // 필수 소모품만
        put("의료·건강", 0.50);     // 보험 해지·정기검진 미룸
        put("경조사", 0.50);        // 축소하되 완전히 끊기 어려움
        put("기타", 0.50);          // 성격 불명. 중간값
        put("교육", 0.30);          // 월 단위라 중단 가능
        put("카페·간식", 0.15);     // 외식보다 먼저 끊음
        put("문화·여가", 0.15);     // 거의 중단
        put("유흥", 0.10);          // 거의 중단
        put("쇼핑", 0.10);          // 거의 중단
        put("여행", 0.00);          // 위기 시 취소
    }};

    private static final double DEFAULT_KEEP_RATE = 0.50;

    /**
     * 시나리오별 영향 카테고리.
     *
     * stress_scenario.target_category가 단일 값 컬럼이라 다중 카테고리를 담지 못한다.
     * 룰북 기준(INFLATION은 식비·생활·교통·쇼핑)을 DB 15종으로 펼쳐 여기에 둔다.
     * 나중에 DB 구조가 바뀌면 이 상수만 지우면 된다.
     */
    private static final Map<String, List<String>> SCENARIO_TARGET =
            new LinkedHashMap<String, List<String>>() {{
                put("INFLATION", java.util.Arrays.asList(
                        "식비", "마트·편의점", "카페·간식", "생활", "기타", "교통", "쇼핑"));
                put("RENT", java.util.Arrays.asList("주거·공과금"));
                // 룰북은 '금융' 대상이나 DB에 금융 카테고리가 없다.
                // 금리가 오르면 전세대출 이자와 월세 전환율이 함께 올라 주거비로 나타난다.
                put("RATE", java.util.Arrays.asList("주거·공과금"));
                put("MEDICAL", java.util.Arrays.asList());   // 카테고리와 무관하게 고정 금액을 더한다
            }};

    /** 시나리오 수치의 산출 근거. 화면에 그대로 보여준다 */
    private static final Map<String, String> SCENARIO_BASIS =
            new LinkedHashMap<String, String>() {{
                put("INFLATION|LOW", "2022년 유가 급등 시 수준입니다. 평년 소비자물가 상승률은 2~3%입니다.");
                put("INFLATION|MID", "오일쇼크 수준의 물가 상승을 가정했습니다.");
                put("INFLATION|HIGH", "경제 위기 수준의 물가 상승을 가정했습니다.");
                put("MEDICAL|LOW", "건강보험심사평가원 통계 기준 1~2일 입원 시 본인부담금입니다.");
                put("MEDICAL|MID", "중간 수준의 수술이 필요한 경우입니다.");
                put("MEDICAL|HIGH", "장기 입원이 필요한 경우입니다.");
                put("RENT|LOW", "계약갱신청구권 상한선 기준입니다.");
                put("RENT|MID", "재계약 시 평균 인상률입니다.");
                put("RENT|HIGH", "부동산 급등기 수준입니다.");
                put("RATE|LOW", "한국은행 기준금리 0.5%p 인상에 해당합니다.");
                put("RATE|MID", "기준금리 1%p 인상에 해당합니다.");
                put("RATE|HIGH", "2022년의 급격한 금리 인상 수준입니다.");
                put("COMPLEX|LOW", "물가·월세·금리·의료비 충격이 동시에 낮은 강도로 발생한 경우입니다.");
                put("COMPLEX|MID", "네 가지 충격이 동시에 보통 강도로 발생한 경우입니다.");
                put("COMPLEX|HIGH", "네 가지 충격이 동시에 높은 강도로 발생한 경우입니다.");
            }};

    /**
     * 연령대별 의료비 기대값 (월 환산, 원).
     * 청년은 실제 의료비 지출이 거의 없어 소비 내역만으로는 위험이 과소평가된다.
     * 건강보험심사평가원 통계의 입원확률 × 평균비용으로 대체한다.
     */
    private static long medicalExpectation(int age) {
        if (age <= 24) return 50_400;
        if (age <= 29) return 68_900;
        if (age <= 34) return 140_400;
        return 216_300;
    }


    /**
     * stress-01: 시나리오 목록
     *
     * DB에는 시나리오 5종 × 강도 3단계가 15행으로 평평하게 들어 있다.
     * 화면은 '시나리오 선택 → 강도 선택' 순서라 코드 단위로 묶어서 내보낸다.
     */
    @Override
    public List<StressScenarioResDto> findScenarios() {
        List<StressScenarioVO> rows = stressMapper.findAllScenarios();

        // 조회 쿼리의 정렬 순서를 유지해야 하므로 LinkedHashMap을 쓴다
        Map<String, List<StressScenarioVO>> grouped = new LinkedHashMap<>();
        for (StressScenarioVO row : rows) {
            grouped.computeIfAbsent(row.getScenarioCode(), k -> new ArrayList<>()).add(row);
        }

        List<StressScenarioResDto> result = new ArrayList<>();

        for (List<StressScenarioVO> levels : grouped.values()) {
            StressScenarioVO head = levels.get(0);

            // COMPLEX는 다른 시나리오를 조합해 계산하므로 수치가 비어 있어도 사용할 수 있다
            boolean available = "COMPLEX".equals(head.getScenarioCode())
                    || levels.stream().anyMatch(
                    v -> v.getChangeRate() != null || v.getFixedAmount() != null);

            List<StressLevelResDto> levelDtos = new ArrayList<>();
            for (StressScenarioVO v : levels) {
                levelDtos.add(StressLevelResDto.builder()
                        .scenarioNo(v.getScenarioNo())
                        .shockLevel(v.getShockLevel())
                        .label(levelLabel(v.getShockLevel()))
                        .changeRate(v.getChangeRate())
                        .fixedAmount(v.getFixedAmount())
                        .displayText(displayText(v))
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


    /**
     * stress-02: 스트레스 테스트 계산
     *
     * 위기 유지율은 점수 계산에 넣지 않는다.
     * 넣으면 '물가가 30% 올랐는데 점수가 오르는' 모순이 생겨 스트레스 테스트가 성립하지 않는다.
     * 대신 지출을 줄였을 때의 개선 효과를 별도로 계산해 권고로 제공한다.
     */
    @Override
    public StressResultResDto calculate(StressResultReqDto req) {

        StressScenarioVO scenario =
                stressMapper.findScenario(req.getScenarioCode(), req.getShockLevel());

        if (scenario == null) {
            return fail("SCENARIO_UNAVAILABLE", "존재하지 않는 시나리오입니다.");
        }

        Integer age = stressMapper.findMemberAge(req.getMemberNo());
        if (age == null) {
            return fail("PROFILE_REQUIRED",
                    "생년월일이 등록되어 있지 않아 계산할 수 없습니다. 프로필을 먼저 입력해주세요.");
        }

        long balance = stressMapper.findTotalBalance(req.getMemberNo());
        if (balance <= 0) {
            return fail("NO_ACCOUNT",
                    "등록된 계좌 잔액이 없어 계산할 수 없습니다. 계좌를 연결해주세요.");
        }

        List<CategorySpendingDto> rows = stressMapper.findMonthlySpending(req.getMemberNo());
        if (rows.isEmpty()) {
            return fail("NO_SPENDING",
                    "최근 소비 내역이 없어 계산할 수 없습니다.");
        }

        // 카테고리별 월 지출을 모으고, 의료·건강만 통계 기대값으로 대체한다
        Map<String, Long> spending = new LinkedHashMap<>();
        int monthCount = 1;
        for (CategorySpendingDto row : rows) {
            spending.put(row.getCategoryName(), row.getMonthlyAmount());
            if (row.getMonthCount() != null && row.getMonthCount() > monthCount) {
                monthCount = row.getMonthCount();
            }
        }
        spending.put("의료·건강", medicalExpectation(age));

        long monthlySpending = spending.values().stream().mapToLong(Long::longValue).sum();

        // 시나리오 증가분
        long increase = calcIncrease(scenario, spending);

        long crisisSpending = monthlySpending + increase;
        double survival = (double) balance / crisisSpending;
        int score = (int) Math.round(Math.min(survival / SCORE_BASE_MONTHS * 100, 100));

        // 지출 구성
        List<String> affected = SCENARIO_TARGET.getOrDefault(scenario.getScenarioCode(), null);
        boolean complex = "COMPLEX".equals(scenario.getScenarioCode());

        List<StressBreakdownDto> breakdown = new ArrayList<>();
        long fixedTotal = 0, variableTotal = 0, adjustableTotal = 0;

        for (Map.Entry<String, Long> e : spending.entrySet()) {
            double keep = keepRate(e.getKey());
            String type = spendingType(keep);
            long reducible = Math.round(e.getValue() * (1 - keep));

            if (keep >= 1.0) fixedTotal += e.getValue();
            else if (keep >= 0.5) variableTotal += e.getValue();
            else adjustableTotal += e.getValue();

            boolean hit = complex
                    ? isComplexTarget(e.getKey())
                    : (affected != null && affected.contains(e.getKey()));

            breakdown.add(StressBreakdownDto.builder()
                    .categoryName(e.getKey())
                    .monthlyAmount(e.getValue())
                    .keepRate(keep)
                    .spendingType(type)
                    .reducibleAmount(reducible)
                    .affected(hit)
                    .build());
        }
        breakdown.sort(Comparator.comparingLong(StressBreakdownDto::getMonthlyAmount).reversed());

        return StressResultResDto.builder()
                .status("OK")
                .message("계산이 완료되었습니다.")
                .scenarioCode(scenario.getScenarioCode())
                .scenarioName(scenario.getScenarioName())
                .shockLevel(scenario.getShockLevel())
                .shockLabel(levelLabel(scenario.getShockLevel()))
                .scenarioBasis(SCENARIO_BASIS.get(
                        scenario.getScenarioCode() + "|" + scenario.getShockLevel()))
                .score(score)
                .grade(grade(score))
                .gradeAction(gradeAction(score))
                .survivalMonths(round1(survival))
                .balance(balance)
                .monthlySpending(monthlySpending)
                .increaseAmount(increase)
                .crisisSpending(crisisSpending)
                .fixedTotal(fixedTotal)
                .variableTotal(variableTotal)
                .adjustableTotal(adjustableTotal)
                .breakdown(breakdown)
                .reduction(buildReduction(spending, balance, increase))
                .basis(buildBasis(scenario, spending, monthCount, age, balance, increase))
                .build();
    }

    /**
     * 시나리오 증가분.
     * COMPLEX는 룰북대로 나머지 네 시나리오를 같은 강도로 동시에 적용한다.
     */
    private long calcIncrease(StressScenarioVO scenario, Map<String, Long> spending) {
        if ("COMPLEX".equals(scenario.getScenarioCode())) {
            long sum = 0;
            for (StressScenarioVO s : stressMapper.findScenariosByLevel(scenario.getShockLevel())) {
                sum += singleIncrease(s, spending);
            }
            return sum;
        }
        return singleIncrease(scenario, spending);
    }

    private long singleIncrease(StressScenarioVO s, Map<String, Long> spending) {
        // 금액형(MEDICAL)은 기존 지출과 무관하게 그대로 더한다
        if (s.getFixedAmount() != null) {
            return s.getFixedAmount();
        }
        if (s.getChangeRate() == null) {
            return 0;
        }
        double rate = s.getChangeRate().doubleValue();
        long base = 0;
        for (String cat : SCENARIO_TARGET.getOrDefault(s.getScenarioCode(),
                java.util.Collections.emptyList())) {
            base += spending.getOrDefault(cat, 0L);
        }
        return Math.round(base * rate);
    }

    /** COMPLEX는 네 시나리오의 대상을 모두 합친 것이 영향 범위다 */
    private boolean isComplexTarget(String category) {
        return SCENARIO_TARGET.values().stream().anyMatch(list -> list.contains(category));
    }

    /**
     * 지출을 유지율대로 줄였을 때의 개선 효과.
     * 위기 상황에서 실제로 줄일 수 있는 만큼만 반영한다.
     */
    private StressReductionDto buildReduction(Map<String, Long> spending,
                                              long balance, long increase) {
        long reduced = 0;
        for (Map.Entry<String, Long> e : spending.entrySet()) {
            reduced += Math.round(e.getValue() * keepRate(e.getKey()));
        }
        long original = spending.values().stream().mapToLong(Long::longValue).sum();

        // 충격은 그대로 온다고 보고 줄인 지출에 더한다
        long crisis = reduced + increase;
        double survival = (double) balance / crisis;
        int score = (int) Math.round(Math.min(survival / SCORE_BASE_MONTHS * 100, 100));

        List<String> top = new ArrayList<>();
        spending.entrySet().stream()
                .map(e -> new Object[]{e.getKey(), Math.round(e.getValue() * (1 - keepRate(e.getKey())))})
                .filter(a -> (Long) a[1] > 0)
                .sorted((a, b) -> Long.compare((Long) b[1], (Long) a[1]))
                .limit(3)
                .forEach(a -> top.add(String.format("%s %,d원 절감", a[0], (Long) a[1])));

        return StressReductionDto.builder()
                .reducedSpending(reduced)
                .savingAmount(original - reduced)
                .reducedSurvivalMonths(round1(survival))
                .reducedScore(score)
                .reducedGrade(grade(score))
                .topSavings(top)
                .build();
    }

    /** 화면에서 '왜 이 점수인지'를 설명할 수 있도록 계산 과정을 문장으로 남긴다 */
    private List<String> buildBasis(StressScenarioVO scenario, Map<String, Long> spending,
                                    int monthCount, int age, long balance, long increase) {
        List<String> basis = new ArrayList<>();
        basis.add(String.format("최근 %d개월 소비 내역을 월평균으로 환산했습니다.", monthCount));
        basis.add(String.format("사용 가능 잔액 %,d원", balance));
        basis.add(String.format("의료비는 실제 지출 대신 %d대 통계 기대값 %,d원을 적용했습니다.",
                age / 10 * 10, medicalExpectation(age)));

        if ("MEDICAL".equals(scenario.getScenarioCode())) {
            basis.add(String.format("의료비 %,d원이 추가로 발생하는 상황입니다.", increase));
        } else if ("COMPLEX".equals(scenario.getScenarioCode())) {
            basis.add(String.format("네 가지 충격이 동시에 발생해 월 %,d원이 늘어납니다.", increase));
        } else {
            List<String> targets = SCENARIO_TARGET.getOrDefault(
                    scenario.getScenarioCode(), java.util.Collections.emptyList());
            long base = targets.stream().mapToLong(c -> spending.getOrDefault(c, 0L)).sum();
            int percent = scenario.getChangeRate() == null ? 0
                    : scenario.getChangeRate().multiply(BigDecimal.valueOf(100)).intValue();
            basis.add(String.format("%s 지출 %,d원의 %d%%인 %,d원이 추가로 발생합니다.",
                    String.join("·", targets), base, percent, increase));
        }

        basis.add("권장 비상자금 6개월치를 100점 기준으로 환산했습니다.");
        return basis;
    }

    private double keepRate(String category) {
        return KEEP_RATE.getOrDefault(category, DEFAULT_KEEP_RATE);
    }

    /** 별도 분류표를 두지 않고 유지율에서 성격을 파생시킨다 */
    private String spendingType(double keep) {
        if (keep >= 1.0) return "고정비";
        if (keep >= 0.5) return "변동비";
        return "조절 가능";
    }

    private String grade(int score) {
        if (score >= 75) return "안전";
        if (score >= 50) return "주의";
        return "위험";
    }

    private String gradeAction(int score) {
        if (score >= 75) return "현재 수준을 유지하세요.";
        if (score >= 50) return "비상자금을 3개월치 더 마련하는 것을 권장합니다.";
        return "즉시 비상금 적립을 시작하세요.";
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private StressResultResDto fail(String status, String message) {
        return StressResultResDto.builder().status(status).message(message).build();
    }

    private String levelLabel(String shockLevel) {
        if ("LOW".equals(shockLevel)) return "낮음";
        if ("MID".equals(shockLevel)) return "보통";
        if ("HIGH".equals(shockLevel)) return "높음";
        return shockLevel;
    }

    /** 비율형과 금액형을 화면에서 같은 자리에 보여주기 위해 서버에서 문구를 만든다 */
    private String displayText(StressScenarioVO v) {
        if (v.getChangeRate() != null) {
            int percent = v.getChangeRate().multiply(BigDecimal.valueOf(100)).intValue();
            return v.getTargetCategory() + " +" + percent + "%";
        }
        if (v.getFixedAmount() != null) {
            long manWon = v.getFixedAmount() / 10000;
            return v.getTargetCategory() + " +" + manWon + "만원";
        }
        // COMPLEX는 다른 시나리오를 같은 강도로 조합한다
        return "물가·월세·금리·의료비 동시";
    }
}