package org.scoula.admin.service;

import lombok.RequiredArgsConstructor;
import org.scoula.admin.constant.ConflictPromptKeys;
import org.scoula.admin.domain.ConflictCandidateVO;
import org.scoula.admin.mapper.ConflictAiMapper;
import org.scoula.admin.mapper.ConflictCandidateMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ConflictGateServiceImpl implements ConflictGateService {

    private final ConflictCandidateMapper mapper;
    private final ConflictAiMapper conflictAiMapper;
    private final ConflictAiService conflictAiService;
    private final PromptService promptService;

    /**
     * 지금 사용중인 분석 세대.
     *
     * 판정과 대조는 모두 한 세대 안에서 이뤄져야 한다.
     * 세대를 모르는 채로 진행하면 예전 분석 결과를 근거로 삼거나
     * 아직 적용하지 않은 세대의 결과를 관리자 화면에 올리게 된다.
     */
    private Integer activeVersion() {
        return promptService.requireActiveVersion(ConflictPromptKeys.CONFLICT_DETECTION);
    }

    // ------------------------------------------------------------
    // Cross-check
    // ------------------------------------------------------------

    /**
     * AI 는 정책 하나만 읽으므로 방향을 알 수 없다.
     * 양방향 근거는 상대 정책의 공고문에서만 나온다.
     *
     * 상대가 후보 필터에 안 걸려 분석되지 않은 경우가 있어
     * "언급 없음" 과 "분석 안 함" 이 구분되지 않았다.
     * 그래서 상대가 특정된 건은 필터를 무시하고 직접 분석한 뒤 대조한다.
     */
    @Override
    public Map<String, Integer> crossCheck() {
        return crossCheckVersion(activeVersion());
    }

    /**
     * 세대를 지정한 상호 대조.
     *
     * 공개 메서드로 열지 않는 이유는, 아무 세대나 실행할 수 있는 입구를
     * 운영 API 로 노출할 필요가 아직 없기 때문이다.
     * 준비 중인 세대를 돌려볼 통로가 필요해지면 그때 통제된 형태로 연다.
     */
    Map<String, Integer> crossCheckVersion(Integer promptVersion) {

        List<ConflictCandidateVO> targets = mapper.findCrosscheckTargets(promptVersion);
        System.out.println("[Cross-check] v" + promptVersion + " 대상 " + targets.size() + "건");

        int mutual = 0, allows = 0, silent = 0, inconclusive = 0, analyzed = 0;

        for (ConflictCandidateVO c : targets) {

            int sourceNo = c.getSourceBenefitNo();
            int mappedNo = c.getMappedBenefitNo();

            // 상대를 이 세대로 분석한다.
            // 같은 본문을 같은 세대로 이미 분석했으면 안에서 건너뛴다.
            // 예전에는 "상대에 저장된 행이 하나라도 있으면 건너뛴다" 로 판단했는데,
            // 그 검사가 세대를 가리지 않아 예전 세대 행 하나 때문에
            // 지금 세대 분석이 통째로 생략될 수 있었다.
            if (conflictAiService.analyzeOneVersion(mappedNo, promptVersion) > 0) {
                analyzed++;
            }

            Set<String> sourceRelations = eligibleRelations(
                    mapper.findPairEvidence(sourceNo, mappedNo, promptVersion));
            Set<String> counterpartRelations = eligibleRelations(
                    mapper.findPairEvidence(mappedNo, sourceNo, promptVersion));

            String result = classifyCrosscheck(sourceRelations, counterpartRelations);
            String direction = MUTUAL.equals(result) ? "BIDIRECTIONAL" : c.getDirection();

            switch (result) {
                case MUTUAL -> {
                    mutual++;
                    System.out.println("[Cross-check] 양방향 확인 "
                            + sourceNo + " <-> " + mappedNo
                            + " (" + c.getTargetNameRaw() + ")");
                }
                case COUNTERPART_ALLOWS -> allows++;
                case INCONCLUSIVE -> {
                    inconclusive++;
                    System.out.println("[Cross-check] 근거가 엇갈림 "
                            + sourceNo + " -> " + mappedNo
                            + " source=" + sourceRelations
                            + " counterpart=" + counterpartRelations);
                }
                default -> silent++;
            }

            mapper.updateCrosscheck(c.getCandidateNo(), result, direction);
        }

        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("target", targets.size());
        out.put("analyzedCounterpart", analyzed);
        out.put(MUTUAL, mutual);
        out.put(COUNTERPART_ALLOWS, allows);
        out.put(COUNTERPART_SILENT, silent);
        out.put(INCONCLUSIVE, inconclusive);
        System.out.println("[Cross-check] " + out);
        return out;
    }

    // ------------------------------------------------------------

    static final String MUTUAL             = "MUTUAL";
    static final String COUNTERPART_ALLOWS = "COUNTERPART_ALLOWS";
    static final String COUNTERPART_SILENT = "COUNTERPART_SILENT";
    static final String INCONCLUSIVE       = "INCONCLUSIVE";

    /**
     * 양쪽 공고가 말한 관계를 놓고 상호 확인 여부를 정한다.
     *
     * 상호 확인은 "양쪽 공식 공고가 독립적으로 서로를 함께 받을 수 없다고 지목했다" 는 뜻이다.
     * 그래서 양쪽 모두 그 하나의 뜻으로 모여야 한다.
     *
     * 한쪽에 금지와 허용이 함께 있으면 그 공고는 하나의 뜻으로 말한 것이 아니다.
     * 금지 근거 하나를 찾았다고 나머지를 버리면
     * 함께 받을 수 있다고 적힌 예외를 못 본 채 자동 차단으로 간다.
     *
     * 근거가 없는 것과 엇갈리는 것은 다르다.
     * 상대가 아무 말도 안 한 것을 엇갈림으로 부르면 안 되고,
     * 엇갈리는 것을 침묵으로 부르면 더 위험하다.
     */
    static String classifyCrosscheck(Set<String> sourceRelations,
                                     Set<String> counterpartRelations) {

        if (counterpartRelations.isEmpty()) {
            return COUNTERPART_SILENT;
        }

        // 상대 공고는 함께 받을 수 있다고만 말했다
        if (counterpartRelations.equals(Set.of("ALLOWED"))) {
            return COUNTERPART_ALLOWS;
        }

        // 양쪽 모두 함께 받을 수 없다는 하나의 뜻으로 모였다
        if (sourceRelations.equals(Set.of("FORBIDDEN"))
                && counterpartRelations.equals(Set.of("FORBIDDEN"))) {
            return MUTUAL;
        }

        // 조건부만 있거나 여러 뜻이 섞여 있다. 사람이 읽어야 한다.
        return INCONCLUSIVE;
    }

    /**
     * 자동 판단의 근거로 쓸 수 있는 행만 남겨 관계 종류를 모은다.
     *
     * 행이 몇 개인지가 아니라 어떤 뜻이 있는지가 중요하다.
     * 같은 뜻이 두 줄로 저장돼 있어도 하나로 본다.
     */
    static Set<String> eligibleRelations(List<ConflictCandidateVO> rows) {
        Set<String> relations = new LinkedHashSet<>();
        for (ConflictCandidateVO row : rows) {
            if (!isUsableEvidence(row)) continue;

            String relation = row.getRelation();
            if (relation == null) continue;

            // 조건이 붙은 금지는 무조건 금지가 아니다.
            // 관계 값이 FORBIDDEN 이어도 조건 유형이 적혀 있으면
            // "어떤 경우에는 함께 받을 수 있다" 는 뜻이 남아 있다.
            // 그것을 무조건 금지와 같은 것으로 세면
            // 상대 공고가 조건부로 말한 것을 근거 삼아 자동 차단으로 간다.
            if ("FORBIDDEN".equals(relation) && row.getConditionType() != null) {
                relations.add("CONDITIONAL");
                continue;
            }
            relations.add(relation);
        }
        return relations;
    }

    /**
     * 이 행을 상호 확인의 근거로 써도 되는가.
     *
     * 검수 대기 상태라는 이유만으로 버리지는 않는다.
     * 방향이나 적용 범위를 아직 못 정했다는 것이지
     * 그 공고가 상대를 지목했다는 사실이 사라지는 것은 아니기 때문이다.
     *
     * 반대로 관리자가 관계가 아니라고 판정했거나 판단을 미룬 건은 쓰지 않는다.
     * 사람이 내린 판단을 시스템이 반대 방향에서 되돌리게 되기 때문이다.
     */
    static boolean isUsableEvidence(ConflictCandidateVO c) {
        // 조회 SQL 도 같은 조건을 걸지만 여기서도 확인한다.
        // 근거 문장에 상대 이름이 없는 건은 AI 가 지어냈을 수 있어
        // 자동 차단의 근거로 쓰면 안 된다.
        // 조회 조건이 나중에 바뀌어도 이 뜻은 지켜져야 한다.
        if (!"Y".equals(c.getEvidenceVerified())) return false;

        if (!"UNIQUE_MATCH".equals(c.getResolverResult())) return false;
        if (c.getMappedBenefitNo() == null) return false;

        String workflow = c.getWorkflowStatus();
        return "UNRESOLVED".equals(workflow)
                || "CONFIRMED".equals(workflow)
                || "REVIEW_REQUIRED".equals(workflow);
    }

    // ------------------------------------------------------------
    // Gate
    // ------------------------------------------------------------

    /**
     * 최종 판정은 AI 가 아니라 여기서 한다.
     * 두 모델이 동의해도 이 조건을 통과하지 못하면 Rule 이 되지 않는다.
     */
    @Override
    public List<Map<String, Object>> applyGate() {
        return applyGateVersion(activeVersion());
    }

    /** 세대를 지정한 판정 */
    List<Map<String, Object>> applyGateVersion(Integer promptVersion) {

        List<ConflictCandidateVO> list = mapper.findUnresolved(promptVersion);
        System.out.println("[Gate] v" + promptVersion + " 대상 " + list.size() + "건");

        for (ConflictCandidateVO c : list) {
            // 정책명 조회는 여기서 하고 판정은 DB 없이 돌게 한다.
            // 판정이 정책명을 쓰지 않고 끝나는 경우에는 조회하지 않는다.
            // 무조건 조회하면 기존보다 쿼리가 늘어난다.
            decide(c, needsPolicyName(c) ? mapper.findPlcyNm(c.getSourceBenefitNo()) : null);
            mapper.updateGate(c);
        }

        List<Map<String, Object>> summary = mapper.gateSummary();
        summary.forEach(row -> System.out.println("[Gate] " + row));
        return summary;
    }

    /**
     * 관리자 Workbench 에 넣을 조건은 하나다.
     *
     *   시스템이 정책 A 와 정책 B 를 이미 특정했는가.
     *
     * 특정하지 못한 건을 보내면 관리자가 "중복 관계 검수" 가 아니라
     * "AI 가 못 끝낸 DB 매칭" 을 대신하게 된다.
     *
     * 다만 특정하지 못한 이유가 둘로 갈린다.
     *   후보가 여럿   → 나중에 좁혀질 수 있으므로 대기
     *   DB 에 없음    → 외부 제도다. 사용자에게 안내는 나가야 한다
     */
    static void decide(ConflictCandidateVO c, String sourcePolicyName) {

        c.setDiscardReason(null);

        // 1. 상대를 이름으로 지목하지 않았다. 범주형이다.
        //    개별 규칙은 못 만들지만 관계 종류에 따라 안내 여부가 갈린다.
        if (c.getTargetNameRaw() == null) {
            setForUnidentifiedTarget(c);
            return;
        }

        // 2. 같은 사업을 상대로 뽑았다. 명백한 추출 오류라 사람에게 보내지 않는다.
        if (isSelfReference(c, sourcePolicyName)) {
            set(c, "DISCARDED", "NONE", null);
            c.setDiscardReason("같은 사업을 상대로 추출함");
            return;
        }

        boolean unique = "UNIQUE_MATCH".equals(c.getResolverResult());
        boolean verified = "Y".equals(c.getEvidenceVerified());

        // 3. 근거 문장에 그 이름이 없다. AI 가 지어냈을 수 있어 이름 자체를 믿을 수 없다.
        if (!verified) {
            if (unique) {
                set(c, "REVIEW_REQUIRED", "NONE", "EXTRACTION_INVALID");
            } else {
                set(c, "DISCARDED", "NONE", null);
                c.setDiscardReason("추출한 이름이 근거 문장에 없고 DB 매칭도 실패");
            }
            return;
        }

        // 4. 이름은 확인됐는데 우리 DB 에 없다. 외부 제도다.
        if ("NO_MATCH".equals(c.getResolverResult())) {
            setForUnidentifiedTarget(c);
            return;
        }

        // 5. 같은 이름의 정책이 여러 곳에 있다.
        //    관리자에게 고르라고 하면 DB 매칭 업무를 떠넘기는 것이므로
        //    버리지 않고 대기시켜 동기화마다 다시 시도한다.
        if (!unique) {
            set(c, "PENDING_DATA", "NONE", null);
            return;
        }

        // 여기부터는 상대가 특정된 관계다. 관계 종류에 따라 갈린다.

        // 5-1. 공고문이 함께 받을 수 있다고 명시한 관계다.
        //      잘못된 분석이 아니라 유효한 사실이므로 확정으로 남기되
        //      추천을 제한하지는 않는다. 상대가 무엇을 말했든 이 사실은 바뀌지 않으므로
        //      cross-check 결과보다 먼저 판단한다.
        if ("ALLOWED".equals(c.getRelation())) {
            set(c, "CONFIRMED", "NONE", null);
            return;
        }

        // 6. 양쪽 공고가 서로 다른 말을 한다. 한쪽이 오래됐을 수 있다.
        //    한쪽에 여러 뜻이 섞여 있는 경우도 여기서 사람에게 넘긴다.
        //    자동으로 어느 근거를 버릴지 정하면 안 되기 때문이다.
        if (COUNTERPART_ALLOWS.equals(c.getCrosscheckResult())
                || INCONCLUSIVE.equals(c.getCrosscheckResult())) {
            set(c, "REVIEW_REQUIRED", "NONE", "CONTRADICTORY_EVIDENCE");
            return;
        }

        // 6-1. 조건에 따라 달라지는 관계다.
        //      conditionType 이 비어 있어도 여기서 걸러야 한다.
        //      AI 가 조건 유형을 빠뜨리면 저장 단계에서 null 이 되는데,
        //      그것을 무조건 관계로 읽으면 조건부가 차단으로 승격된다.
        if ("CONDITIONAL".equals(c.getRelation())) {
            set(c, "REVIEW_REQUIRED", "NONE", "CONDITIONAL");
            return;
        }

        // 7. 양쪽 공고가 독립적으로 서로를 지목했다.
        //    한쪽만 보고는 알 수 없는 근거이므로 자동 확정한다.
        //    자동 차단은 함께 받을 수 없다고 말한 관계에만 허용한다.
        if ("FORBIDDEN".equals(c.getRelation())
                && MUTUAL.equals(c.getCrosscheckResult())
                && "YES".equals(c.getCombinationApplicability())
                && c.getConditionType() == null) {
            set(c, "CONFIRMED", "CONFIRMED_BLOCK", null);
            c.setConflictDecision("BLOCK");
            return;
        }

        // 8. 아는 관계가 아닌데 상대는 특정됐다.
        //    A 와 B 가 모두 특정됐으므로 사람이 근거 문장을 읽고 판단할 수 있다.
        //    다만 검수로 온 이유는 방향이나 적용 범위가 아니라 관계 값 자체가 이상한 것이다.
        //    reviewReasonOf 를 그대로 쓰면 DIRECTION_UNKNOWN 으로 기록되어
        //    관리자가 엉뚱한 것을 확인하게 된다.
        if (!"FORBIDDEN".equals(c.getRelation())) {
            set(c, "REVIEW_REQUIRED", "NONE", "EXTRACTION_INVALID");
            return;
        }

        // 9. A 와 B 는 특정됐고 근거도 확인됐다. 관계 판단만 남았다.
        set(c, "REVIEW_REQUIRED", "NONE", reviewReasonOf(c));
    }

    /**
     * 상대를 개별 정책으로 특정하지 못한 관계의 처리.
     *
     * 범주만 적혔거나 우리 DB 에 없는 외부 제도인 경우다.
     * 개별쌍 규칙은 만들 수 없지만 안내는 나갈 수 있다.
     *
     * 다만 공고문이 함께 받을 수 있다고 말한 관계까지 안내로 내보내면
     * 원문과 정반대되는 문구가 사용자에게 나간다.
     * 그래서 관계 종류를 먼저 본다.
     */
    private static void setForUnidentifiedTarget(ConflictCandidateVO c) {

        String relation = c.getRelation();

        if ("ALLOWED".equals(relation)) {
            set(c, "CONFIRMED", "NONE", null);
            return;
        }

        // 함께 받을 수 없다는 관계와 조건에 따라 달라진다는 관계는
        // 둘 다 확인해볼 가치가 있으므로 안내로 내린다.
        // 문구를 다르게 만드는 일은 ConflictRuleTextBuilder 가 한다.
        if ("FORBIDDEN".equals(relation) || "CONDITIONAL".equals(relation)) {
            set(c, "CONFIRMED", "WARNING", null);
            return;
        }

        // 아는 관계가 아니다.
        // relation 값은 AI 출력에서 그대로 오는데 저장 전에 검증하는 곳이 없다.
        // 값이 비었거나 오타이거나 새 값이면 무엇을 말하는지 알 수 없으므로
        // 사용자에게 제한 안내로 내보내지 않는다.
        set(c, "DISCARDED", "NONE", null);
        c.setDiscardReason("알 수 없는 관계 값: " + relation);
    }

    /** 왜 검수로 왔는지. 화면에서 사람 말로 바꿔 보여준다 */
    private static String reviewReasonOf(ConflictCandidateVO c) {
        if (c.getConditionType() != null)                  return "CONDITIONAL";
        if (!"BIDIRECTIONAL".equals(c.getDirection()))     return "DIRECTION_UNKNOWN";
        if (!"YES".equals(c.getCombinationApplicability()))
            return "COMBINATION_APPLICABILITY_UNKNOWN";
        return "RELATION_CHECK";
    }

    /**
     * 같은 사업의 다른 유형이나 회차를 상대로 뽑는 경우가 있다.
     *
     * 단순 포함관계로 보면 과잉 폐기가 난다.
     * "고성군 자격증 응시료 지원" 이 "자격증 응시료 지원" 을 지목한 것은
     * 자기 자신이 아니라 다른 지자체의 같은 종류 사업이다.
     *
     * 차이는 무엇이 빠졌는가에 있다.
     *   연도·괄호·차수가 빠졌다   → 같은 사업
     *   지역명이나 기관명이 빠졌다 → 다른 지역의 같은 종류 사업
     */
    static boolean isSelfReference(ConflictCandidateVO c, String sourcePolicyName) {

        if (c.getMappedBenefitNo() != null
                && c.getMappedBenefitNo().equals(c.getSourceBenefitNo())) {
            return true;
        }

        if (sourcePolicyName == null) return false;

        String a = stripNoise(sourcePolicyName);
        String b = stripNoise(c.getTargetNameRaw());
        if (a.isEmpty() || b.isEmpty()) return false;

        if (a.equals(b)) return true;

        if (a.contains(b)) {
            return a.replace(b, "").length() <= 4;
        }
        if (b.contains(a)) {
            return b.replace(a, "").length() <= 4;
        }
        return false;
    }

    /**
     * 연도·괄호·차수처럼 같은 사업 안에서만 달라지는 부분을 걷어낸다.
     * 지역명과 기관명은 남긴다. 그것이 다른 사업과의 유일한 구분자이기 때문이다.
     */
    private static String stripNoise(String name) {
        if (name == null) return "";
        String t = name;
        t = t.replaceAll("\\([^)]*\\)", "");
        t = t.replaceAll("\\[[^\\]]*\\]", "");
        t = t.replaceAll("20\\d{2}\\s*년?", "");
        t = t.replaceAll("\\d{2}\\s*년", "");
        t = t.replaceAll("\\d+\\s*차", "");
        t = t.replaceAll("제\\s*\\d+\\s*기", "");
        return ConflictNormalizer.compact(t);
    }

    private static void set(ConflictCandidateVO c, String workflow, String enforcement, String reason) {
        c.setWorkflowStatus(workflow);
        c.setEnforcementState(enforcement);
        c.setReviewReason(reason);
    }

    /**
     * 판정이 source 정책명을 실제로 쓰는가.
     *
     * 범주형(1번)과 자기 매핑(2번 앞부분)은 정책명 없이 끝난다.
     * 기존 구현도 그 경우에는 findPlcyNm 을 부르지 않았으므로
     * 조회 횟수를 그대로 유지하려면 같은 조건이 필요하다.
     */
    private static boolean needsPolicyName(ConflictCandidateVO c) {
        if (c.getTargetNameRaw() == null) return false;
        return !(c.getMappedBenefitNo() != null
                && c.getMappedBenefitNo().equals(c.getSourceBenefitNo()));
    }

    // ------------------------------------------------------------
    // 동기화 재해소
    // ------------------------------------------------------------

    /**
     * 동기화 뒤에 부른다.
     *
     * 지난번엔 상대를 못 골랐지만 그 사이에 새 정책이 들어왔거나
     * 중복 적재가 정리됐으면 이제 하나로 좁혀질 수 있다.
     * 관리자에게 시키지 않는 대신 시스템이 계속 다시 시도한다.
     */
    @Override
    public Map<String, Integer> reResolve() {
        return reResolveVersion(activeVersion());
    }

    /**
     * 세대를 지정한 재해소.
     *
     * 이 경로는 조회로 끝나지 않고 Candidate 를 실제로 고친다.
     * 세대를 가리지 않으면 아직 적용하지 않은 세대의 행까지 건드리게 된다.
     */
    Map<String, Integer> reResolveVersion(Integer promptVersion) {

        List<ConflictCandidateVO> list = mapper.findResolvableAgain(promptVersion);
        System.out.println("[재해소] v" + promptVersion + " 대상 " + list.size() + "건");

        ConflictResolver resolver = new ConflictResolver(conflictAiMapper.findAllBenefitNames());
        int promoted = 0, still = 0;

        for (ConflictCandidateVO c : list) {
            ConflictResolver.Result r =
                    resolver.resolve(c.getTargetNameRaw(), c.getSourceBenefitNo());

            if ("UNIQUE_MATCH".equals(r.getResolverResult())) {
                mapper.updateResolve(c.getCandidateNo(), r.getMappedBenefitNo(),
                        "UNIQUE_MATCH", null, "UNRESOLVED", null);
                promoted++;
                System.out.println("[재해소] 승격 candidate=" + c.getCandidateNo()
                        + " (" + c.getTargetNameRaw() + ")"
                        + " → benefit_no=" + r.getMappedBenefitNo());
            } else {
                mapper.touchResolve(c.getCandidateNo());
                still++;
            }
        }

        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("target", list.size());
        out.put("promoted", promoted);
        out.put("stillPending", still);
        System.out.println("[재해소] " + out);
        return out;
    }
}