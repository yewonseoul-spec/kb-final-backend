package org.scoula.admin.service;

import lombok.RequiredArgsConstructor;
import org.scoula.admin.domain.ConflictCandidateVO;
import org.scoula.admin.mapper.ConflictAiMapper;
import org.scoula.admin.mapper.ConflictCandidateMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConflictGateServiceImpl implements ConflictGateService {

    private final ConflictCandidateMapper mapper;
    private final ConflictAiMapper conflictAiMapper;
    private final ConflictAiService conflictAiService;

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

        List<ConflictCandidateVO> targets = mapper.findCrosscheckTargets();
        System.out.println("[Cross-check] 대상 " + targets.size() + "건");

        int mutual = 0, allows = 0, silent = 0, analyzed = 0;

        for (ConflictCandidateVO c : targets) {

            // 상대가 아직 분석되지 않았으면 지금 분석한다.
            // 이미 분석된 정책이면 내부에서 건너뛴다.
            if (mapper.countBySource(c.getMappedBenefitNo()) == 0) {
                if (conflictAiService.analyzeOne(c.getMappedBenefitNo()) > 0) {
                    analyzed++;
                }
            }

            String counterpart = mapper.findCounterpartRelation(
                    c.getSourceBenefitNo(), c.getMappedBenefitNo());

            String result;
            String direction = c.getDirection();

            if (counterpart == null) {
                // 상대 공고를 실제로 읽었는데 우리 얘기가 없다.
                // 충돌이 아니라는 뜻은 아니므로 그대로 둔다.
                result = "COUNTERPART_SILENT";
                silent++;

            } else if ("ALLOWED".equals(counterpart)) {
                // 한쪽은 안 된다 하고 한쪽은 된다고 한다.
                // 공고 시점이 다를 수 있어 자동 판단하지 않는다.
                result = "COUNTERPART_ALLOWS";
                allows++;

            } else {
                result = "MUTUAL";
                direction = "BIDIRECTIONAL";
                mutual++;

                // 같은 관계인데 두 공고문을 따로 읽어 applicability 가 갈릴 수 있다.
                // 상호 확인된 관계라면 근거가 강한 쪽을 양쪽에 적용하는 것이 맞다.
                if ("YES".equals(c.getCombinationApplicability())) {
                    mapper.propagateApplicability(
                            c.getMappedBenefitNo(), c.getSourceBenefitNo());
                }

                System.out.println("[Cross-check] 양방향 확인 "
                        + c.getSourceBenefitNo() + " <-> " + c.getMappedBenefitNo()
                        + " (" + c.getTargetNameRaw() + ")");
            }

            mapper.updateCrosscheck(c.getCandidateNo(), result, direction);
        }

        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("target", targets.size());
        out.put("analyzedCounterpart", analyzed);
        out.put("MUTUAL", mutual);
        out.put("COUNTERPART_ALLOWS", allows);
        out.put("COUNTERPART_SILENT", silent);
        System.out.println("[Cross-check] " + out);
        return out;
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

        List<ConflictCandidateVO> list = mapper.findUnresolved();
        System.out.println("[Gate] 대상 " + list.size() + "건");

        for (ConflictCandidateVO c : list) {
            decide(c);
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
    private void decide(ConflictCandidateVO c) {

        c.setDiscardReason(null);

        // 1. 상대를 이름으로 지목하지 않았다. 범주형이다.
        if (c.getTargetNameRaw() == null) {
            set(c, "CONFIRMED", "WARNING", null);
            return;
        }

        // 2. 같은 사업을 상대로 뽑았다. 명백한 추출 오류라 사람에게 보내지 않는다.
        if (isSelfReference(c)) {
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
            set(c, "CONFIRMED", "WARNING", null);
            return;
        }

        // 5. 같은 이름의 정책이 여러 곳에 있다.
        //    관리자에게 고르라고 하면 DB 매칭 업무를 떠넘기는 것이므로
        //    버리지 않고 대기시켜 동기화마다 다시 시도한다.
        if (!unique) {
            set(c, "PENDING_DATA", "NONE", null);
            return;
        }

        // 6. 양쪽 공고가 서로 다른 말을 한다. 한쪽이 오래됐을 수 있다.
        if ("COUNTERPART_ALLOWS".equals(c.getCrosscheckResult())) {
            set(c, "REVIEW_REQUIRED", "NONE", "CONTRADICTORY_EVIDENCE");
            return;
        }

        // 7. 양쪽 공고가 독립적으로 서로를 지목했다.
        //    한쪽만 보고는 알 수 없는 근거이므로 자동 확정한다.
        if ("MUTUAL".equals(c.getCrosscheckResult())
                && "YES".equals(c.getCombinationApplicability())
                && c.getConditionType() == null) {
            set(c, "CONFIRMED", "CONFIRMED_BLOCK", null);
            c.setConflictDecision("BLOCK");
            return;
        }

        // 8. A 와 B 는 특정됐고 근거도 확인됐다. 관계 판단만 남았다.
        set(c, "REVIEW_REQUIRED", "NONE", reviewReasonOf(c));
    }

    /** 왜 검수로 왔는지. 화면에서 사람 말로 바꿔 보여준다 */
    private String reviewReasonOf(ConflictCandidateVO c) {
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
    private boolean isSelfReference(ConflictCandidateVO c) {

        if (c.getMappedBenefitNo() != null
                && c.getMappedBenefitNo().equals(c.getSourceBenefitNo())) {
            return true;
        }

        String src = mapper.findPlcyNm(c.getSourceBenefitNo());
        if (src == null) return false;

        String a = stripNoise(src);
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
    private String stripNoise(String name) {
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

    private void set(ConflictCandidateVO c, String workflow, String enforcement, String reason) {
        c.setWorkflowStatus(workflow);
        c.setEnforcementState(enforcement);
        c.setReviewReason(reason);
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

        List<ConflictCandidateVO> list = mapper.findResolvableAgain();
        System.out.println("[재해소] 대상 " + list.size() + "건");

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