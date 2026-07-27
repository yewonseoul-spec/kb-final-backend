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

    // ===== engine-05 점수 기준 =====
    private static final int TOP_K = 20;

    private static final int SCORE_POPULAR_HIGH = 10;
    private static final int SCORE_POPULAR_MID = 7;
    private static final int SCORE_POPULAR_LOW = 4;
    private static final int SCORE_POPULAR_BASE = 1;
    private static final int INQ_HIGH = 1000;
    private static final int INQ_MID = 300;
    private static final int INQ_LOW = 100;

    private static final int SCORE_INCOME_CERTAIN = 40;
    private static final int SCORE_INCOME_UNSURE = 10;

    private static final int SCORE_DEADLINE_URGENT = 30;
    private static final int SCORE_DEADLINE_SOON = 20;
    private static final int SCORE_DEADLINE_ALWAYS = 15;
    private static final int SCORE_DEADLINE_FAR = 10;
    private static final int SCORE_DEADLINE_CLOSED = 0;
    private static final int DAYS_URGENT = 30;
    private static final int DAYS_SOON = 90;

    private static final int SCORE_NO_WARNING = 20;

    // ===== engine-06 조합 평가 기준 =====
    private static final int MAX_COMBINATION_SIZE = 3;   // 조합에 담을 최대 정책 수
    private static final int RECOMMEND_COUNT = 1;        // 최종 추천 조합 개수
    private static final int SIZE_BONUS = 3;             // 정책 1개 늘 때마다 가점
    private static final int DIVERSITY_BONUS = 4;        // 카테고리 1종 늘 때마다 가점
    private static final int MAX_OVERLAP = 1;            // 유사 조합 판정 기준(공통 정책 수)

    private static final Comparator<BenefitResDto> RANKING =
            Comparator.<BenefitResDto>comparingInt(BenefitResDto::getScore).reversed()
                    .thenComparing(BenefitResDto::getApplyEndDate,
                            Comparator.nullsLast(Comparator.<Date>naturalOrder()))
                    .thenComparingInt(BenefitResDto::getBenefitNo);

    /**
     * 조합 정렬 규칙
     *   1순위 조합 점수 높은 순
     *   2순위 경고가 적은 순
     *   3순위 정책 수 많은 순
     *   4순위 정책번호 합계 오름차순 — 결정론 보장
     */
    private static final Comparator<CombinationResDto> COMBINATION_RANKING =
            Comparator.<CombinationResDto>comparingDouble(CombinationResDto::getCombinationScore).reversed()
                    // 경고가 적은 조합 우선
                    .thenComparingInt(c -> c.getWarnings().size())
                    // 정책 수가 많은 조합 우선
                    .thenComparing(Comparator.comparingInt(
                            (CombinationResDto c) -> c.getBenefits().size()).reversed())
                    // 조합 안에 약한 정책이 끼지 않도록 최저 점수가 높은 쪽 우선
                    .thenComparing(Comparator.comparingInt(
                            EngineServiceImpl::minPolicyScore).reversed())
                    // 신청 기회를 놓치지 않도록 마감이 빠른 쪽 우선 (마감일 없으면 뒤로)
                    .thenComparing(EngineServiceImpl::earliestDeadline,
                            Comparator.nullsLast(Comparator.<Date>naturalOrder()))
                    // 조회수는 로그로 완화해 극단값 하나가 지배하지 않게 한다
                    .thenComparing(Comparator.comparingDouble(
                            EngineServiceImpl::popularityTieScore).reversed())
                    // 마지막은 결정론 확보용 (화면에 노출하지 않는다)
                    .thenComparingInt(EngineServiceImpl::sumBenefitNo);
    // ==============================================

    @Override
    public EngineResultDto findEligibleBenefits(int memberNo) {
        UserProfileResDto profile = engineMapper.findUserProfile(memberNo);
        if (profile == null) {
            throw new RuntimeException("회원 프로필 없음. memberNo=" + memberNo);
        }

        List<BenefitResDto> benefits =
                removeDuplicatePolicies(engineMapper.findEligibleBenefits(profile));

        Set<Integer> candidateNos = benefits.stream()
                .map(BenefitResDto::getBenefitNo)
                .collect(Collectors.toSet());

        List<Integer> appliedNos = engineMapper.findAppliedBenefitNos(memberNo);

        List<ConflictWarningDto> warnings = appliedNos.isEmpty()
                ? List.of()
                : engineMapper.findConflictWarnings(appliedNos).stream()
                .filter(w -> candidateNos.contains(w.getBenefitNo()))
                .collect(Collectors.toList());

        List<ConflictWarningDto> externalWarnings = engineMapper.findExternalWarnings().stream()
                .filter(w -> candidateNos.contains(w.getBenefitNo()))
                .collect(Collectors.toList());

        warnings = removeDuplicateRuleText(warnings);
        externalWarnings = removeDuplicateRuleText(externalWarnings);

        Set<Integer> internallyWarnedNos = new HashSet<>();
        warnings.forEach(w -> internallyWarnedNos.add(w.getBenefitNo()));

        benefits.forEach(b -> applyScore(b, internallyWarnedNos));

        List<BenefitResDto> topBenefits = benefits.stream()
                .sorted(RANKING)
                .limit(TOP_K)
                .collect(Collectors.toList());

        // engine-06: 상위 K개로 조합 생성 → 평가 → 다양성 고려해 최대 3개 선택
        List<CombinationResDto> recommendedCombinations = recommendCombinations(topBenefits);

        return new EngineResultDto(
                benefits, topBenefits, recommendedCombinations, warnings, externalWarnings);
    }

    // ================= engine-06 =================

    /** 상위 K개 후보로 1~3개 조합을 만들고, 평가 후 서로 겹치지 않는 상위 조합을 고른다. */
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
     * 단순 합계를 쓰면 3개 조합이 항상 이기고, 단순 평균만 쓰면 1개 조합이 유리해진다.
     * 지원 금액을 의미하는 점수가 아니라, 후보군 안에서의 상대 비교용이다.
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
     * 상위 조합이 서로 너무 비슷해지지 않도록, 이미 고른 조합과 공통 정책이
     * MAX_OVERLAP개 이하인 것만 고른다. 개수가 모자라면 조건을 풀어 채운다.
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
        // 조건이 빡빡해 3개를 못 채웠으면 남은 것 중 점수 높은 순으로 채운다
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

    private static int sumBenefitNo(CombinationResDto combo) {
        return combo.getBenefits().stream()
                .mapToInt(BenefitResDto::getBenefitNo).sum();
    }
    /** 조합 구성 정책의 조회수 합계 — 동점 시 인기도로 비교 */
    /** 조합 내 가장 낮은 정책 점수 — 약한 정책이 평균에 가려지는 것을 막는다 */
    private static int minPolicyScore(CombinationResDto combo) {
        return combo.getBenefits().stream()
                .mapToInt(BenefitResDto::getScore)
                .min().orElse(0);
    }

    /** 조합 내 가장 빠른 마감일 — 전부 상시모집이면 null */
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

    // ================= engine-05 이하 (기존과 동일) =================

    private List<BenefitResDto> removeDuplicatePolicies(List<BenefitResDto> source) {
        Map<String, BenefitResDto> unique = new LinkedHashMap<>();
        for (BenefitResDto candidate : source) {
            String key = (candidate.getPlcyNm() == null)
                    ? "NO_NAME_" + candidate.getBenefitNo()
                    : candidate.getPlcyNm().trim();
            BenefitResDto kept = unique.get(key);
            if (kept == null || isMoreComplete(candidate, kept)) {
                unique.put(key, candidate);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private boolean isMoreComplete(BenefitResDto candidate, BenefitResDto kept) {
        boolean candidateHasDate = candidate.getApplyEndDate() != null;
        boolean keptHasDate = kept.getApplyEndDate() != null;
        if (candidateHasDate != keptHasDate) {
            return candidateHasDate;
        }
        return candidate.getBenefitNo() < kept.getBenefitNo();
    }

    private List<ConflictWarningDto> removeDuplicateRuleText(List<ConflictWarningDto> source) {
        Map<String, ConflictWarningDto> unique = new LinkedHashMap<>();
        source.forEach(w -> unique.putIfAbsent(w.getRuleText(), w));
        return new ArrayList<>(unique.values());
    }

    private void applyScore(BenefitResDto benefit, Set<Integer> internallyWarnedNos) {
        List<String> reasons = new ArrayList<>();
        int score = applyPopularity(benefit.getInqCnt(), reasons);

        reasons.add("연령 조건 충족");
        reasons.add("취업 조건 충족");
        reasons.add("학력 조건 충족");
        reasons.add("전공 조건 충족");
        reasons.add("혼인 조건 충족");

        String earnCode = benefit.getEarnCndSeCd();
        if ("0043001".equals(earnCode)) {
            score += SCORE_INCOME_CERTAIN;
            reasons.add("소득 조건 없음");
        } else if ("0043002".equals(earnCode)) {
            score += SCORE_INCOME_CERTAIN;
            reasons.add("소득 조건 충족 확인");
        } else {
            score += SCORE_INCOME_UNSURE;
            reasons.add("소득 조건 별도 확인 필요");
        }

        score += applyDeadline(benefit.getApplyEndDate(), reasons);

        if (internallyWarnedNos.contains(benefit.getBenefitNo())) {
            reasons.add("중복수혜 확인 필요");
        } else {
            score += SCORE_NO_WARNING;
            reasons.add("중복수혜 충돌 없음");
        }

        benefit.setScore(score);
        benefit.setScoreDetail(reasons);
    }

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