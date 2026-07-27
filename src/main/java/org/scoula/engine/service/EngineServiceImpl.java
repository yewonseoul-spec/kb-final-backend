package org.scoula.engine.service;

import lombok.RequiredArgsConstructor;
import org.scoula.engine.dto.BenefitResDto;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    // 인기도 — 지원 금액 데이터가 없어 조회수를 관심도 대체 지표로 사용
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

    /**
     * 정렬 규칙 (동점 처리)
     *   1순위 점수 높은 순
     *   2순위 마감일 가까운 순 (상시모집은 뒤로)
     *   3순위 정책번호 오름차순 — 같은 입력이면 항상 같은 순서 보장
     */
    private static final Comparator<BenefitResDto> RANKING =
            Comparator.<BenefitResDto>comparingInt(BenefitResDto::getScore).reversed()
                    .thenComparing(BenefitResDto::getApplyEndDate,
                            Comparator.nullsLast(Comparator.<Date>naturalOrder()))
                    .thenComparingInt(BenefitResDto::getBenefitNo);
    // ========================================================

    @Override
    public EngineResultDto findEligibleBenefits(int memberNo) {
        // 1. 사용자 프로필 조회
        UserProfileResDto profile = engineMapper.findUserProfile(memberNo);
        if (profile == null) {
            throw new RuntimeException("회원 프로필 없음. memberNo=" + memberNo);
        }

        // 2. 자격조건 + 그룹충돌 + 개별쌍 중복불가가 적용된 후보 조회 (engine-01·02, 룰북 6번)
        //    동기화가 같은 정책을 여러 번 적재하므로 정책명 기준으로 정리한다
        List<BenefitResDto> benefits =
                removeDuplicatePolicies(engineMapper.findEligibleBenefits(profile));

        // 3. 후보 번호 집합 — 경고 필터의 기준이므로 먼저 만든다
        Set<Integer> candidateNos = benefits.stream()
                .map(BenefitResDto::getBenefitNo)
                .collect(Collectors.toSet());

        // 4. 보유 정책 번호 조회 (룰북 2번: IS_ACTIVE 필터 적용 안 함)
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
        //    외부 제도 경고는 '안내만' 하는 것이므로 감점 대상에서 제외한다 (룰북 9장)
        Set<Integer> internallyWarnedNos = new HashSet<>();
        warnings.forEach(w -> internallyWarnedNos.add(w.getBenefitNo()));

        benefits.forEach(b -> applyScore(b, internallyWarnedNos));

        // 9. 상위 K개 선별 (룰북 8번)
        List<BenefitResDto> topBenefits = benefits.stream()
                .sorted(RANKING)
                .limit(TOP_K)
                .collect(Collectors.toList());

        return new EngineResultDto(benefits, topBenefits, warnings, externalWarnings);
    }

    /**
     * 동기화 중복 적재 대응 — 같은 정책명은 하나만 남긴다.
     * 중복 쌍은 마감일만 다른 경우가 많아(작은 번호가 NULL),
     * 마감일 정보가 있는 쪽을 우선한다.
     */
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
     */
    private void applyScore(BenefitResDto benefit, Set<Integer> internallyWarnedNos) {
        List<String> reasons = new ArrayList<>();

        // ① 인기도 (조회수)
        int score = applyPopularity(benefit.getInqCnt(), reasons);

        // ② SQL 필터를 통과했다는 것은 아래 조건을 모두 만족했다는 뜻
        reasons.add("연령 조건 충족");
        reasons.add("취업 조건 충족");
        reasons.add("학력 조건 충족");
        reasons.add("전공 조건 충족");
        reasons.add("혼인 조건 충족");

        // ③ 소득 조건
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

        // ④ 마감 임박도
        score += applyDeadline(benefit.getApplyEndDate(), reasons);

        // ⑤ 중복수혜 충돌 (내부 경고만 반영)
        if (internallyWarnedNos.contains(benefit.getBenefitNo())) {
            reasons.add("중복수혜 확인 필요");
        } else {
            score += SCORE_NO_WARNING;
            reasons.add("중복수혜 충돌 없음");
        }

        benefit.setScore(score);
        benefit.setScoreDetail(reasons);
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