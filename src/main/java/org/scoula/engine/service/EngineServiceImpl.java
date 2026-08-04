package org.scoula.engine.service;

import lombok.RequiredArgsConstructor;
import org.scoula.engine.dto.BenefitResDto;
import org.scoula.engine.dto.CombinationResDto;
import org.scoula.engine.dto.ConflictRuleDto;
import org.scoula.engine.dto.ConflictWarningDto;
import org.scoula.engine.dto.EngineResultDto;
import org.scoula.engine.dto.UserProfileResDto;
import org.scoula.engine.mapper.EngineMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EngineServiceImpl implements EngineService {

    private final EngineMapper engineMapper;

    // ===== engine-05 점수 기준 (팀 합의 시 이 숫자만 수정) =====
    private static final int TOP_K = 20;

    // 인기도 — support_amount가 전부 NULL이라 조회수를 관심도 대체 지표로 사용
    private static final int SCORE_POPULAR_HIGH = 10;
    private static final int SCORE_POPULAR_MID = 7;
    private static final int SCORE_POPULAR_LOW = 4;
    private static final int SCORE_POPULAR_BASE = 1;
    private static final int INQ_HIGH = 1000;
    private static final int INQ_MID = 300;
    private static final int INQ_LOW = 100;

    // 자격 확실성 — 소득조건을 SQL로 검증했는지
    private static final int SCORE_INCOME_CERTAIN = 40;
    private static final int SCORE_INCOME_UNSURE = 10;

    // 시급성 — 마감까지 남은 기간
    private static final int SCORE_DEADLINE_URGENT = 30;
    private static final int SCORE_DEADLINE_SOON = 20;
    private static final int SCORE_DEADLINE_ALWAYS = 15;
    private static final int SCORE_DEADLINE_FAR = 10;
    private static final int SCORE_DEADLINE_CLOSED = 0;
    private static final int DAYS_URGENT = 30;
    private static final int DAYS_SOON = 90;

    // 중복수혜 충돌 없음
    private static final int SCORE_NO_WARNING = 20;

    // ===== engine-06 조합 평가 기준 =====
    private static final int MAX_COMBINATION_SIZE = 3;   // 조합에 담을 최대 정책 수
    private static final int RECOMMEND_COUNT = 1;        // 최종 추천 조합 개수 (룰북 17번)
    private static final int SIZE_BONUS = 3;             // 정책 1개 늘 때마다 가점
    private static final int DIVERSITY_BONUS = 4;        // 카테고리 1종 늘 때마다 가점
    private static final int MAX_OVERLAP = 0;            // RECOMMEND_COUNT가 2 이상일 때만 쓰인다

    // ===== 프로필 필수 항목 =====
    private static final List<String> REQUIRED_PROFILE_FIELDS = List.of(
            "birthDate", "income", "employStatus", "major",
            "education", "mrgSttsCd", "regionCode");

    /**
     * 정책 정렬 규칙 (engine-05 동점 처리)
     *   1 점수 높은 순
     *   2 마감일 가까운 순 (상시모집은 뒤로)
     *   3 정책번호 오름차순 — 같은 입력이면 항상 같은 순서 보장
     */
    private static final Comparator<BenefitResDto> RANKING =
            Comparator.<BenefitResDto>comparingInt(BenefitResDto::getScore).reversed()
                    .thenComparing(BenefitResDto::getApplyEndDate,
                            Comparator.nullsLast(Comparator.<Date>naturalOrder()))
                    .thenComparingInt(BenefitResDto::getBenefitNo);

    /**
     * 조합 정렬 규칙 (engine-06 동점 처리)
     *   1 정책 수 많은 순 — 정책 3개 조합을 항상 우선한다
     *   2 조합 점수 높은 순
     *   3 내부 경고가 적은 순
     *   4 조합 내 최저 정책점수 높은 순 — 약한 정책이 평균에 가려지는 것 방지
     *   5 가장 빠른 마감일 (상시모집은 뒤로) — 같은 구간 안에서 시급성 구분
     *   6 로그 조회수 합계 높은 순 — 극단적 인기 정책 하나가 조합을 지배하는 것을 완화
     *   7 정렬된 정책번호 목록 — 결정론 보장용. 화면에 노출하지 않는다
     */
    private static final Comparator<CombinationResDto> COMBINATION_RANKING =
            Comparator.<CombinationResDto>comparingInt(c -> c.getBenefits().size()).reversed()
                    .thenComparing(Comparator.<CombinationResDto>comparingDouble(
                            CombinationResDto::getCombinationScore).reversed())
                    .thenComparingInt(c -> c.getWarnings().size())
                    .thenComparing(Comparator.comparingInt(
                            EngineServiceImpl::minPolicyScore).reversed())
                    .thenComparing(EngineServiceImpl::earliestDeadline,
                            Comparator.nullsLast(Comparator.<Date>naturalOrder()))
                    .thenComparing(Comparator.comparingDouble(
                            EngineServiceImpl::popularityTieScore).reversed())
                    .thenComparing(EngineServiceImpl::benefitNoKey);
    // ==============================================

    @Override
    public EngineResultDto findEligibleBenefits(int memberNo) {
        // 1. 사용자 프로필 조회
        UserProfileResDto profile = engineMapper.findUserProfile(memberNo);

        // 프로필이 없거나 필수 항목이 비면 추천 SQL을 부르지 않고 안내만 반환한다
        if (profile == null) {
            return EngineResultDto.profileRequired(REQUIRED_PROFILE_FIELDS);
        }
        List<String> missingFields = findMissingProfileFields(profile);
        if (!missingFields.isEmpty()) {
            return EngineResultDto.profileRequired(missingFields);
        }

        // 2. 자격조건 + 그룹충돌 + 개별쌍 중복불가가 적용된 후보 조회 (engine-01·02, 룰북 6번)
        //    동기화가 같은 정책을 여러 번 적재하므로 정책명 기준으로 정리한다
        List<BenefitResDto> benefits =
                removeDuplicatePolicies(engineMapper.findEligibleBenefits(profile));

        // 3. 후보 번호 집합 — 경고 필터의 기준이므로 먼저 만든다
        Set<Integer> candidateNos = benefits.stream()
                .map(BenefitResDto::getBenefitNo)
                .collect(Collectors.toSet());

        // 4. 보유 정책 번호 조회 (룰북 2번: is_active 필터 적용 안 함)
        List<Integer> appliedNos = engineMapper.findAppliedBenefitNos(memberNo);

        // 5. 보유 정책과의 경고 — 후보에 있는 정책만 남긴다 (engine-03)
        List<ConflictWarningDto> warnings = appliedNos.isEmpty()
                ? List.of()
                : engineMapper.findConflictWarnings(appliedNos).stream()
                .filter(w -> candidateNos.contains(w.getBenefitNo()))
                .collect(Collectors.toList());

        // 6. 외부 제도 경고 — 후보에 있는 정책만 남긴다 (engine-04)
        List<ConflictWarningDto> externalWarnings = engineMapper.findExternalWarnings().stream()
                .filter(w -> candidateNos.contains(w.getBenefitNo()))
                .collect(Collectors.toList());

        // 7. 룰북 15번 — 같은 경고 문구는 화면에 한 번만 표시
        warnings = removeDuplicateRuleText(warnings);
        externalWarnings = removeDuplicateRuleText(externalWarnings);

        // 8. 점수 계산 (engine-05)
        //    외부 제도 경고는 '안내만' 하는 것이므로 감점 대상에서 제외한다 (룰북 14번)
        Set<Integer> internallyWarnedNos = new HashSet<>();
        warnings.forEach(w -> internallyWarnedNos.add(w.getBenefitNo()));

        benefits.forEach(b -> applyScore(b, internallyWarnedNos));

        // 9. 상위 K개 선별 (룰북 8번)
        List<BenefitResDto> topBenefits = benefits.stream()
                .sorted(RANKING)
                .limit(TOP_K)
                .collect(Collectors.toList());

        // 10. 조합 생성 → 내부 충돌 검사 → 평가 → 최적 조합 반환 (engine-06)
        List<CombinationResDto> recommendedCombinations = recommendCombinations(topBenefits);

        return new EngineResultDto(
                "OK", List.of(),
                benefits, topBenefits, recommendedCombinations, warnings, externalWarnings);
    }

    // ================= 프로필 검사 =================

    /**
     * 추천 SQL을 호출하기 전에 프로필 필수 항목이 채워졌는지 확인한다.
     * 값이 비면 조건 비교가 NULL이 되어 정책이 조용히 탈락하므로, 미리 걸러서 안내한다.
     */
    private List<String> findMissingProfileFields(UserProfileResDto profile) {
        List<String> missing = new ArrayList<>();

        if (profile.getBirthDate() == null)     missing.add("birthDate");
        if (profile.getIncome() == null)        missing.add("income");
        if (isBlank(profile.getEmployStatus())) missing.add("employStatus");
        if (isBlank(profile.getMajor()))        missing.add("major");
        if (isBlank(profile.getEducation()))    missing.add("education");
        if (isBlank(profile.getMrgSttsCd()))    missing.add("mrgSttsCd");
        if (isBlank(profile.getRegionCode()))   missing.add("regionCode");

        return missing;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // ================= engine-06 조합 =================

    /** 상위 K개 후보로 1~3개 조합을 만들고, 평가 후 최적 조합을 고른다. */
    private List<CombinationResDto> recommendCombinations(List<BenefitResDto> topBenefits) {
        // 규칙은 DB에서 한 번만 조회한다 (조합마다 SQL 호출 금지)
        List<ConflictRuleDto> rules = engineMapper.findConfirmedInternalRules();

        Set<String> blockedPairs = new HashSet<>();          // 중복불가 쌍
        Map<String, String> warningPairs = new HashMap<>();  // 경고 쌍 → 문구

        for (ConflictRuleDto rule : rules) {
            if (rule.getTriggerBenefitNo() == null || rule.getTargetBenefitNo() == null) {
                continue;
            }
            String key = pairKey(rule.getTriggerBenefitNo(), rule.getTargetBenefitNo());
            if ("중복불가".equals(rule.getConflictType())) {
                blockedPairs.add(key);
            } else {
                warningPairs.put(key, rule.getRuleText());
            }
        }

        List<CombinationResDto> valid = new ArrayList<>();
        int size = topBenefits.size();

        // 1개 조합
        for (int i = 0; i < size; i++) {
            List<BenefitResDto> combo = List.of(topBenefits.get(i));
            valid.add(evaluateCombination(combo, warningPairs));
        }
        // 2개 조합
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                List<BenefitResDto> combo = List.of(topBenefits.get(i), topBenefits.get(j));
                if (hasInternalConflict(combo, blockedPairs)) continue;
                valid.add(evaluateCombination(combo, warningPairs));
            }
        }
        // 3개 조합
        if (MAX_COMBINATION_SIZE >= 3) {
            for (int i = 0; i < size; i++) {
                for (int j = i + 1; j < size; j++) {
                    for (int k = j + 1; k < size; k++) {
                        List<BenefitResDto> combo = List.of(
                                topBenefits.get(i), topBenefits.get(j), topBenefits.get(k));
                        if (hasInternalConflict(combo, blockedPairs)) continue;
                        valid.add(evaluateCombination(combo, warningPairs));
                    }
                }
            }
        }

        valid.sort(COMBINATION_RANKING);
        return selectDiverse(valid);
    }

    /**
     * 조합 내부 충돌 검사 (룰북 10·11번)
     *   - 같은 conflict_group_code가 둘 이상이면 제외
     *   - 확정 중복불가 정책쌍이 있으면 제외
     */
    private boolean hasInternalConflict(List<BenefitResDto> combo, Set<String> blockedPairs) {
        Set<String> groupCodes = new HashSet<>();
        for (BenefitResDto b : combo) {
            String group = b.getConflictGroupCode();
            if (group != null && !group.trim().isEmpty() && !groupCodes.add(group.trim())) {
                return true;   // 같은 그룹 중복
            }
        }
        for (int i = 0; i < combo.size(); i++) {
            for (int j = i + 1; j < combo.size(); j++) {
                String key = pairKey(combo.get(i).getBenefitNo(), combo.get(j).getBenefitNo());
                if (blockedPairs.contains(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 조합 점수 = 평균 점수 + 정책 수 보너스 + 카테고리 다양성 보너스
     * 단순 합계는 3개 조합이 항상 이기고, 단순 평균은 1개 조합이 유리해져서 절충한 것이다.
     * 지원 금액을 의미하는 점수가 아니라 후보군 안에서의 상대 비교용이다.
     */
    private CombinationResDto evaluateCombination(List<BenefitResDto> combo,
                                                  Map<String, String> warningPairs) {
        double totalScore = combo.stream().mapToInt(BenefitResDto::getScore).sum();
        double averageScore = totalScore / combo.size();

        Set<String> categories = combo.stream()
                .map(BenefitResDto::getCategoryCode)
                .filter(c -> c != null)
                .collect(Collectors.toSet());

        int sizeBonus = (combo.size() - 1) * SIZE_BONUS;
        int diversityBonus = (categories.size() - 1) * DIVERSITY_BONUS;
        double combinationScore = averageScore + sizeBonus + diversityBonus;

        // 조합 내부 경고 수집 (룰북 12번) — 같은 문구는 한 번만 (룰북 15번)
        Set<String> comboWarnings = new LinkedHashSet<>();
        for (int i = 0; i < combo.size(); i++) {
            for (int j = i + 1; j < combo.size(); j++) {
                String text = warningPairs.get(
                        pairKey(combo.get(i).getBenefitNo(), combo.get(j).getBenefitNo()));
                if (text != null) {
                    comboWarnings.add(text);
                }
            }
        }

        List<String> detail = new ArrayList<>();
        detail.add("평균 점수 " + String.format("%.1f", averageScore));
        detail.add("정책 " + combo.size() + "개 (+" + sizeBonus + ")");
        detail.add("카테고리 " + categories.size() + "종 (+" + diversityBonus + ")");

        return new CombinationResDto(
                combo,
                Math.round(combinationScore * 10) / 10.0,
                Math.round(averageScore * 10) / 10.0,
                categories.size(),
                new ArrayList<>(comboWarnings),
                detail);
    }

    /**
     * 상위 조합이 서로 비슷해지지 않도록, 이미 고른 조합과 공통 정책이
     * MAX_OVERLAP개 이하인 것만 고른다. 개수가 모자라면 조건을 풀어 채운다.
     * RECOMMEND_COUNT가 1이면 사실상 1위 하나만 반환된다.
     */
    private List<CombinationResDto> selectDiverse(List<CombinationResDto> sorted) {
        List<CombinationResDto> selected = new ArrayList<>();

        for (CombinationResDto candidate : sorted) {
            if (selected.size() >= RECOMMEND_COUNT) break;
            boolean tooSimilar = selected.stream()
                    .anyMatch(chosen -> overlapCount(chosen, candidate) > MAX_OVERLAP);
            if (!tooSimilar) {
                selected.add(candidate);
            }
        }
        // 조건이 빡빡해 개수를 못 채웠으면 남은 것 중 점수 높은 순으로 채운다
        for (CombinationResDto candidate : sorted) {
            if (selected.size() >= RECOMMEND_COUNT) break;
            boolean already = selected.stream().anyMatch(chosen -> chosen == candidate);
            if (!already) {
                selected.add(candidate);
            }
        }
        return selected;
    }

    private int overlapCount(CombinationResDto a, CombinationResDto b) {
        Set<Integer> nosA = a.getBenefits().stream()
                .map(BenefitResDto::getBenefitNo)
                .collect(Collectors.toSet());
        return (int) b.getBenefits().stream()
                .filter(x -> nosA.contains(x.getBenefitNo()))
                .count();
    }

    /** 정책쌍은 순서 없는 관계이므로 작은 번호를 앞에 두어 키를 통일한다 */
    private String pairKey(int a, int b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }

    // ----- 조합 동점 처리용 -----

    /** 조합 내 가장 낮은 정책 점수 — 약한 정책이 평균에 가려지는 것을 막는다 */
    private static int minPolicyScore(CombinationResDto combo) {
        return combo.getBenefits().stream()
                .mapToInt(BenefitResDto::getScore)
                .min().orElse(0);
    }

    /** 조합 내 가장 빠른 마감일. 전부 상시모집이면 null (정렬에서 뒤로) */
    private static Date earliestDeadline(CombinationResDto combo) {
        return combo.getBenefits().stream()
                .map(BenefitResDto::getApplyEndDate)
                .filter(d -> d != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /** 로그 조회수 합계 — 조회수 3만짜리 하나가 조합 전체를 지배하는 것을 완화 */
    private static double popularityTieScore(CombinationResDto combo) {
        return combo.getBenefits().stream()
                .mapToDouble(b -> Math.log1p(b.getInqCnt() == null ? 0 : b.getInqCnt()))
                .sum();
    }

    /**
     * 정렬된 정책번호 목록을 문자열 키로 만든다.
     * 합계는 1+5와 2+4가 같아 구분이 안 되므로 목록으로 비교한다.
     * 제로 패딩을 넣어야 사전순 비교가 숫자순과 일치한다.
     */
    private static String benefitNoKey(CombinationResDto combo) {
        return combo.getBenefits().stream()
                .map(BenefitResDto::getBenefitNo)
                .sorted()
                .map(no -> String.format("%06d", no))
                .collect(Collectors.joining(","));
    }

    // ================= engine-05 점수 =================

    /**
     * 동기화 중복 적재 대응 — 같은 정책명은 하나만 남긴다.
     * 실제 데이터 확인 결과 동일 정책명은 전부 동일 기관이라 오제거 위험이 없다.
     * 중복 쌍은 마감일만 다른 경우가 많아(작은 번호가 NULL) 마감일 정보가 있는 쪽을 우선한다.
     */
    private List<BenefitResDto> removeDuplicatePolicies(List<BenefitResDto> source) {
        Map<String, BenefitResDto> unique = new LinkedHashMap<>();

        for (BenefitResDto candidate : source) {
            String key = (candidate.getPlcyNm() == null)
                    ? "NO_NAME_" + candidate.getBenefitNo()
                    : candidate.getPlcyNm().trim().replaceAll("\\s+", " ");

            BenefitResDto kept = unique.get(key);
            if (kept == null || isMoreComplete(candidate, kept)) {
                unique.put(key, candidate);
            }
        }
        return new ArrayList<>(unique.values());
    }

    /** 마감일이 있는 쪽 우선, 조건이 같으면 정책번호가 작은 쪽 */
    private boolean isMoreComplete(BenefitResDto candidate, BenefitResDto kept) {
        boolean candidateHasDate = candidate.getApplyEndDate() != null;
        boolean keptHasDate = kept.getApplyEndDate() != null;

        if (candidateHasDate != keptHasDate) {
            return candidateHasDate;
        }
        return candidate.getBenefitNo() < kept.getBenefitNo();
    }

    /**
     * 룰북 15번 — 같은 RULE_TEXT가 여러 경로로 반복되면 한 번만 남긴다.
     * 서로 다른 문구는 각각 유지한다.
     */
    private List<ConflictWarningDto> removeDuplicateRuleText(List<ConflictWarningDto> source) {
        Map<String, ConflictWarningDto> unique = new LinkedHashMap<>();
        source.forEach(w -> unique.putIfAbsent(w.getRuleText(), w));
        return new ArrayList<>(unique.values());
    }

    /**
     * engine-05: 점수와 매칭 근거를 함께 계산해 DTO에 세팅한다.
     * support_amount 컬럼은 있으나 현재 데이터가 전부 NULL이라
     * 금액 기준 대신 '인기도 + 자격 확실성 + 시급성'으로 우선순위를 정한다.
     *
     * 근거는 '확인 필요'를 앞에, '충족'을 뒤에 둔다.
     * 열 줄이 전부 '충족'으로 나열되면 정작 사용자가 챙겨야 할 항목이 묻히기 때문이다.
     */
    private void applyScore(BenefitResDto benefit, Set<Integer> internallyWarnedNos) {
        List<String> needCheck = new ArrayList<>();   // 사용자가 직접 확인해야 하는 것
        List<String> confirmed = new ArrayList<>();   // SQL로 판정이 끝난 것

        // ① 인기도 (조회수)
        int score = applyPopularity(benefit.getInqCnt(), confirmed);

        // ② SQL 필터를 통과했다는 것은 아래 조건을 모두 만족했다는 뜻
        confirmed.add("연령 조건 충족");
        confirmed.add("취업 조건 충족");
        confirmed.add("학력 조건 충족");
        confirmed.add("전공 조건 충족");
        confirmed.add("혼인 조건 충족");
        confirmed.add("거주 지역 조건 충족");

        // ③ 소득 조건
        // 0043003(기타)은 조건이 자연어라 SQL로 판정할 수 없어 일단 통과시킨 것이다.
        // '충족'이라고 쓰면 자격이 확인된 것처럼 읽히므로 원문을 그대로 붙여 안내한다.
        String earnCode = benefit.getEarnCndSeCd();
        if ("0043001".equals(earnCode)) {
            score += SCORE_INCOME_CERTAIN;
            confirmed.add("소득 조건 없음");
        } else if ("0043002".equals(earnCode)) {
            score += SCORE_INCOME_CERTAIN;
            confirmed.add("소득 조건 충족 확인");
        } else {
            score += SCORE_INCOME_UNSURE;
            needCheck.add(buildIncomeCheckText(benefit.getEarnEtcCn()));
        }

        // ④ 마감 임박도
        score += applyDeadline(benefit.getApplyEndDate(), confirmed);

        // ⑤ 중복수혜 충돌 (내부 경고만 반영)
        if (internallyWarnedNos.contains(benefit.getBenefitNo())) {
            needCheck.add("중복수혜 확인 필요");
        } else {
            score += SCORE_NO_WARNING;
            confirmed.add("중복수혜 충돌 없음");
        }

        List<String> reasons = new ArrayList<>(needCheck.size() + confirmed.size());
        reasons.addAll(needCheck);
        reasons.addAll(confirmed);

        benefit.setScore(score);
        benefit.setScoreDetail(reasons);
    }

    /**
     * 기타 소득조건 안내 문구.
     * earn_etc_cn에 '중위소득 150% 이하' 같은 실제 조건이 들어 있어 그대로 보여준다.
     * 값이 비어 있으면 문구만 남긴다.
     */
    private String buildIncomeCheckText(String earnEtcCn) {
        if (earnEtcCn == null || earnEtcCn.trim().isEmpty()) {
            return "소득 조건 확인 필요";
        }
        return "소득 조건 확인 필요 — " + earnEtcCn.trim();
    }

    /** 조회수 구간별 인기도 점수. 분포가 크게 치우쳐 있어 구간으로 나눈다. */
    private int applyPopularity(Integer inqCnt, List<String> reasons) {
        int count = (inqCnt == null) ? 0 : inqCnt;

        if (count >= INQ_HIGH) {
            reasons.add("관심 많은 정책 (조회 " + count + "회)");
            return SCORE_POPULAR_HIGH;
        }
        if (count >= INQ_MID) {
            reasons.add("조회 " + count + "회");
            return SCORE_POPULAR_MID;
        }
        if (count >= INQ_LOW) {
            reasons.add("조회 " + count + "회");
            return SCORE_POPULAR_LOW;
        }
        reasons.add("조회 " + count + "회");
        return SCORE_POPULAR_BASE;
    }

    /**
     * 마감일까지 남은 '날짜 수'로 판정한다.
     * 밀리초 차이로 계산하면 당일 마감 정책이 잘못 판정될 수 있어 LocalDate를 사용한다.
     */
    private int applyDeadline(Date applyEndDate, List<String> reasons) {
        if (applyEndDate == null) {
            reasons.add("상시 모집");
            return SCORE_DEADLINE_ALWAYS;
        }

        LocalDate today = LocalDate.now();
        LocalDate endDate = new java.sql.Date(applyEndDate.getTime()).toLocalDate();
        long remainDays = ChronoUnit.DAYS.between(today, endDate);

        if (remainDays < 0) {
            reasons.add("접수 마감됨");
            return SCORE_DEADLINE_CLOSED;
        }
        if (remainDays <= DAYS_URGENT) {
            reasons.add("마감 임박 (D-" + remainDays + ")");
            return SCORE_DEADLINE_URGENT;
        }
        if (remainDays <= DAYS_SOON) {
            reasons.add("마감 " + remainDays + "일 남음");
            return SCORE_DEADLINE_SOON;
        }
        reasons.add("접수 기간 여유 있음");
        return SCORE_DEADLINE_FAR;
    }
}