package org.scoula.admin.service;

import org.scoula.admin.domain.ConflictObservationVO;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 같은 의미로 들어온 여러 AI 관측을 실행 근거 하나로 정리한다.
 *
 * AI 는 한 번의 응답에서 같은 상대를 두 번 말할 수 있다.
 * 실제로 "청년월세지원" 과 "청년 월세 지원" 처럼 표기만 다르게 두 줄을 내면
 * 공백을 지운 뒤에는 같은 의미가 되는데, 두 줄의 세부 값이 서로 다를 수 있다.
 *
 * 지금까지는 INSERT IGNORE 가 먼저 들어온 줄만 남겼다.
 * 그러면 AI 가 "적용된다" 와 "모르겠다" 를 함께 말했을 때
 * 어느 줄이 먼저 저장됐는지에 따라 자동 차단 여부가 갈린다.
 * 같은 응답을 두 번 저장하면 결과가 달라진다는 뜻이다.
 *
 * 여기서 정하는 원칙은 하나다.
 * 관측이 엇갈렸다는 사실이 확신을 높이는 방향으로 쓰이면 안 된다.
 * 그래서 값이 갈리면 확정값을 고르지 않고 모른다는 값으로 모은다.
 *
 * 자동 차단은 다음 네 가지가 모두 맞을 때만 일어난다.
 *
 *     relation=FORBIDDEN · crosscheck=MUTUAL
 *     combinationApplicability=YES · conditionType=null
 *
 * 이 중 뒤 두 개가 여기서 정해진다.
 * 갈린 값을 UNKNOWN 으로 모으고 조건 유형을 남기는 선택은
 * 둘 다 자동 차단을 막는 쪽으로만 작동한다.
 *
 * 이 클래스는 DB 를 모른다.
 * 입력은 AI 가 공고문에서 뽑은 사실뿐이고, 그 사실들을 어떻게 하나로 볼지만 정한다.
 * 상대 정책 번호나 판정 결과는 애초에 관측에 들어 있지 않으므로
 * 여기서 그것들을 참고할 방법도 없다.
 *
 * @author 박상호
 * @since 2026-08-21
 */
public final class ConflictCanonicalReconciler {

    private ConflictCanonicalReconciler() {}

    /** 관측들이 한 가지 뜻으로 모였다 */
    public static final String STABLE = "STABLE";

    /** 자동 판단에 쓰이는 값에서 관측이 엇갈렸다 */
    public static final String CONFLICTING = "CONFLICTING";

    /**
     * 서로 다른 조건 유형이 함께 나왔다.
     *
     * AI 가 말한 값이 아니라 정리 단계에서 붙이는 값이다.
     * 원문에 무엇이 적혀 있었는지는 관측 기록에 그대로 남아 있고,
     * 여기서는 "조건이 하나로 정해지지 않았다" 는 사실만 표시한다.
     *
     * 실제 조건 유형 중 하나를 골라 적으면 안 된다.
     * AMOUNT_ADJUSTMENT 는 안내 문구를 일부제한으로 바꾸므로
     * 어느 것을 고르느냐에 따라 사용자에게 나가는 말이 달라진다.
     * 근거가 갈린 상태에서 그중 하나를 사실처럼 적을 수는 없다.
     */
    public static final String MULTIPLE_CONDITIONS = "MULTIPLE_CONDITIONS";

    static final String UNKNOWN = "UNKNOWN";

    /**
     * 여러 관측을 정리한 결과.
     *
     * 원본 관측을 대체하는 것이 아니라, 그 관측들을 봤을 때
     * 시스템이 실행 근거로 삼아도 되는 값이 무엇인지를 담는다.
     */
    public record Canonical(
            String combinationApplicability,
            String conditionType,
            String conditionText,
            String timing,
            String subjectScope,
            String restrictionStage,
            String direction,
            String triggerScope,
            String evidenceVerified,
            String evidenceText,
            Double confidence,
            int observationCount,
            String reconciliationStatus
    ) {}

    /**
     * 같은 의미의 관측 여러 건을 하나로 정리한다.
     *
     * 입력 순서가 결과를 바꾸면 안 된다.
     * 저장 순서나 AI 응답 순서는 사실과 아무 관계가 없기 때문이다.
     * 그래서 어느 값을 고를 때도 "먼저 온 것" 을 쓰지 않고
     * 값 자체로 정해지는 기준만 쓴다.
     *
     * 관계 종류와 상대 이름은 여기서 정하지 않는다.
     * 그 둘은 같은 의미인지를 가르는 기준이므로
     * 여기 들어온 관측들은 이미 같은 값을 갖고 있다.
     */
    public static Canonical reconcile(List<ConflictObservationVO> observations) {

        if (observations == null || observations.isEmpty()) {
            throw new IllegalArgumentException("정리할 관측이 없습니다");
        }

        String applicability = agreedOrUnknown(
                observations, ConflictObservationVO::getCombinationApplicability);

        String conditionType = reconcileConditionType(observations);

        return new Canonical(
                applicability,
                conditionType,
                reconcileConditionText(observations, conditionType),
                agreedOrUnknown(observations, ConflictObservationVO::getTiming),
                agreedOrUnknown(observations, ConflictObservationVO::getSubjectScope),
                agreedOrUnknown(observations, ConflictObservationVO::getRestrictionStage),
                agreedOrUnknown(observations, ConflictObservationVO::getDirection),
                agreedOrUnknown(observations, ConflictObservationVO::getTriggerScope),
                reconcileEvidenceVerified(observations),
                reconcileEvidenceText(observations),
                lowestConfidence(observations),
                observations.size(),
                statusOf(observations)
        );
    }

    /**
     * 모든 관측이 같은 값을 말했으면 그 값을, 하나라도 다르면 모른다는 값을 준다.
     *
     * 값이 비어 있는 것도 하나의 관측으로 센다.
     * AI 가 한 줄에서는 "적용된다" 고 하고 다른 줄에서는 아무 말도 안 했다면
     * 그것도 엇갈린 것이지 "적용된다" 로 확정된 것이 아니다.
     *
     * 다만 모든 관측이 비어 있으면 UNKNOWN 이 아니라 그대로 비워 둔다.
     * AI 가 말하지 않은 것과 여러 번 말했는데 갈린 것은 다른 상태다.
     */
    static String agreedOrUnknown(List<ConflictObservationVO> rows,
                                  Function<ConflictObservationVO, String> getter) {

        Set<String> values = new LinkedHashSet<>();
        for (ConflictObservationVO row : rows) {
            values.add(blankToNull(getter.apply(row)));
        }
        return values.size() == 1 ? values.iterator().next() : UNKNOWN;
    }

    /**
     * 조건 유형은 갈렸을 때 비워두면 안 된다.
     *
     * 이 값이 비어 있으면 자동 차단이 열린다.
     * AI 가 한 줄에서라도 조건을 말했다면 조건 없는 금지가 아니므로
     * 다른 줄이 비어 있다는 이유로 그 사실을 버리지 않는다.
     *
     * 서로 다른 조건 유형이 함께 나왔을 때는 그중 하나를 고르지 않는다.
     * 자동 차단을 막는다는 목적으로는 어느 것을 골라도 같지만,
     * 사용자에게 나가는 문구는 값에 따라 달라진다.
     * 조건이 무엇인지 정해지지 않았다는 것을 그대로 적는 편이 정확하다.
     */
    static String reconcileConditionType(List<ConflictObservationVO> rows) {

        Set<String> types = new LinkedHashSet<>();
        for (ConflictObservationVO row : rows) {
            String value = blankToNull(row.getConditionType());
            if (value != null) types.add(value);
        }

        if (types.isEmpty()) return null;
        if (types.size() == 1) return types.iterator().next();
        return MULTIPLE_CONDITIONS;
    }

    /**
     * 조건 문장은 채택한 조건 유형을 말한 관측에서 가져온다.
     * 유형과 문장이 서로 다른 관측에서 오면 관리자가 읽을 때 앞뒤가 안 맞는다.
     *
     * 유형이 하나로 정해지지 않았으면 문장도 비워 둔다.
     * 갈린 조건 중 한 문장만 올려두면 그것이 확정된 조건처럼 읽힌다.
     * 원문 문장은 관측 기록에 전부 남아 있으므로 여기서 버리는 것이 아니다.
     */
    static String reconcileConditionText(List<ConflictObservationVO> rows, String conditionType) {

        if (conditionType == null || MULTIPLE_CONDITIONS.equals(conditionType)) return null;

        String best = null;
        for (ConflictObservationVO row : rows) {
            if (!conditionType.equals(blankToNull(row.getConditionType()))) continue;
            best = longer(best, blankToNull(row.getConditionText()));
        }
        return best;
    }

    /**
     * 한 번이라도 근거 문장에서 이름을 확인했으면 확인된 것으로 본다.
     *
     * 이 값이 뜻하는 것은 다음 하나다.
     *
     *     이 관계를 뒷받침하는, 이름이 확인된 관측이 최소 하나 있다.
     *
     * "정리된 모든 값이 확인된 근거에서 나왔다" 는 뜻이 아니다.
     * 확인되지 않은 관측의 값도 정리 대상에 함께 들어가는데,
     * 그쪽 값이 더 강하다고 해서 채택되지는 않는다.
     * 값이 갈리면 어느 관측이 확인됐는지와 무관하게 모른다는 값으로 모으기 때문이다.
     *
     * 확인되지 않은 것으로 모으면 그 관계가 상호 대조에서 아예 빠진다.
     * 특히 상대 공고의 허용 근거가 그렇게 사라지면
     * 남은 금지 근거만으로 상호 확인이 성립해 자동 차단으로 간다.
     * 근거를 지우는 쪽이 더 위험하므로 확인된 쪽을 남긴다.
     *
     * 관계 종류에 따라 이 기준을 다르게 두지 않는다.
     * 확인 여부는 그 관계를 뒷받침하는 공식 문장이 있느냐의 문제이지
     * 금지인지 허용인지의 문제가 아니다.
     */
    static String reconcileEvidenceVerified(List<ConflictObservationVO> rows) {

        for (ConflictObservationVO row : rows) {
            if ("Y".equals(row.getEvidenceVerified())) return "Y";
        }
        return "N";
    }

    /**
     * 관리자가 읽을 대표 문장.
     *
     * 확인된 관측이 하나라도 있으면 대표 문장도 반드시 그쪽에서 나와야 한다.
     * 확인된 것으로 표시해 놓고 확인되지 않은 관측의 문장을 올리면
     * 관리자가 읽는 근거와 시스템이 근거로 삼은 것이 달라진다.
     * 그래서 확인된 관측에 쓸 문장이 없으면 비워 두고, 다른 쪽에서 가져오지 않는다.
     *
     * 긴 쪽을 고르는 것은 앞뒤 맥락이 더 담겨 있을 가능성이 높아서이지
     * 더 정확하다고 보기 때문이 아니다. 화면 표시를 위한 기준일 뿐이다.
     */
    static String reconcileEvidenceText(List<ConflictObservationVO> rows) {

        boolean hasVerified = "Y".equals(reconcileEvidenceVerified(rows));

        String best = null;
        for (ConflictObservationVO row : rows) {
            if (hasVerified && !"Y".equals(row.getEvidenceVerified())) continue;
            best = longer(best, blankToNull(row.getEvidenceText()));
        }
        return best;
    }

    /**
     * AI 자기보고 점수는 가장 낮은 것을 남긴다.
     * 자동 확정 근거로는 쓰지 않지만, 여러 관측이 있었다는 사정을
     * 화면에서 높은 쪽으로 보이게 할 이유가 없다.
     */
    static Double lowestConfidence(List<ConflictObservationVO> rows) {

        Double lowest = null;
        for (ConflictObservationVO row : rows) {
            Double value = row.getConfidence();
            if (value == null) continue;
            if (lowest == null || value < lowest) lowest = value;
        }
        return lowest;
    }

    /**
     * 자동 판단에 직접 쓰이는 값에서 관측이 갈렸는가.
     *
     * 나머지 값도 갈릴 수 있지만 그쪽은 안내 문구에만 영향을 준다.
     * 여기서는 자동 차단 여부를 가르는 두 가지만 본다.
     */
    static String statusOf(List<ConflictObservationVO> rows) {

        boolean applicabilitySplit = distinctCount(
                rows, ConflictObservationVO::getCombinationApplicability) > 1;
        boolean conditionSplit = distinctCount(
                rows, ConflictObservationVO::getConditionType) > 1;

        return (applicabilitySplit || conditionSplit) ? CONFLICTING : STABLE;
    }

    private static int distinctCount(List<ConflictObservationVO> rows,
                                     Function<ConflictObservationVO, String> getter) {
        Set<String> values = new LinkedHashSet<>();
        for (ConflictObservationVO row : rows) {
            values.add(blankToNull(getter.apply(row)));
        }
        return values.size();
    }

    /** 긴 쪽. 길이가 같으면 사전순으로 앞선 쪽을 써서 순서에 흔들리지 않게 한다 */
    private static String longer(String kept, String candidate) {
        if (candidate == null) return kept;
        if (kept == null) return candidate;
        if (candidate.length() != kept.length()) {
            return candidate.length() > kept.length() ? candidate : kept;
        }
        return candidate.compareTo(kept) < 0 ? candidate : kept;
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
