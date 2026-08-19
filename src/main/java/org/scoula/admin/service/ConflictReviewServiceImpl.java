package org.scoula.admin.service;

import lombok.RequiredArgsConstructor;
import org.scoula.admin.domain.ConflictCandidateVO;
import org.scoula.admin.mapper.ConflictCandidateMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConflictReviewServiceImpl implements ConflictReviewService {

    private final ConflictCandidateMapper mapper;

    /**
     * 검수 대기 목록.
     *
     * A 와 B 가 이미 특정된 것만 온다. 관리자가 고를 상대를 찾는 일은 없다.
     * 그래도 후보 목록이 남아 있는 건은 참고용으로 이름을 붙여 내린다.
     */
    @Override
    public List<Map<String, Object>> queue() {

        List<Map<String, Object>> rows = mapper.findReviewQueue();

        for (Map<String, Object> row : rows) {
            Object raw = row.get("resolver_candidates");
            if (raw == null || raw.toString().trim().isEmpty()) continue;

            List<Integer> nos = new ArrayList<>();
            for (String s : raw.toString().split(",")) {
                try {
                    nos.add(Integer.parseInt(s.trim()));
                } catch (NumberFormatException ignore) {
                    // 저장이 깨진 값 하나 때문에 목록 전체가 죽으면 안 된다
                }
            }
            if (nos.isEmpty()) continue;

            row.put("candidate_briefs", mapper.findBenefitBriefs(nos));
        }
        return rows;
    }

    @Override
    public Map<String, Object> detail(int candidateNo) {
        return mapper.findReviewDetail(candidateNo);
    }

    /**
     * 관리자 판정.
     *
     * decision 은 관리자가 누른 버튼이고, 그것이 곧 엔진 동작은 아니다.
     * 예를 들어 "중복 불가" 를 눌러도 한쪽 공고만 근거가 있으면
     * 대칭 규칙으로 만들지 않고 안내로 내린다.
     * 관리자는 근거 문장 하나를 본 것이지 양쪽을 본 것이 아니기 때문이다.
     */
    @Override
    public void decide(int candidateNo, String decision, Integer mappedNo,
                       String reason, Integer memberNo) {

        if (mappedNo != null) {
            mapper.fixMapping(candidateNo, mappedNo);
        }

        ConflictCandidateVO c = mapper.findByNo(candidateNo);

        switch (decision == null ? "" : decision) {

            case "BLOCK": {
                // 양방향 근거가 있을 때만 조합에서 제거한다.
                // 한쪽 공고만 지목한 관계를 대칭으로 만들면
                // 반대편 사용자가 받을 수 있었던 정책을 잃는다.
                boolean twoWay = "BIDIRECTIONAL".equals(c.getDirection());
                mapper.decide(candidateNo, "CONFIRMED",
                        twoWay ? "CONFIRMED_BLOCK" : "WARNING",
                        "BLOCK", null, null, memberNo);
                break;
            }

            case "PARTIAL": {
                // 금액이 조정되는 경우. 함께 받을 수는 있으므로 제거하지 않는다
                mapper.decide(candidateNo, "CONFIRMED", "WARNING",
                        "PARTIAL", null, null, memberNo);
                break;
            }

            case "NOT_CONFLICT": {
                mapper.decide(candidateNo, "DISCARDED", "NONE",
                        "NOT_CONFLICT", null,
                        (reason == null || reason.trim().isEmpty())
                                ? "관리자 판단: 중복 관계 아님" : reason,
                        memberNo);
                break;
            }

            default:
                throw new IllegalArgumentException("알 수 없는 판정입니다: " + decision);
        }
    }

    /**
     * 관리자가 "판단 보류" 를 눌렀다고 정책 관계의 안전성이 바뀌는 것은 아니다.
     * 그래서 잠금은 유지한다. 다만 영구히 잠기면 안 되므로 만료를 둔다.
     */
    @Override
    public void defer(int candidateNo, int days, Integer memberNo) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, days <= 0 ? 7 : days);
        ConflictCandidateVO c = mapper.findByNo(candidateNo);
        mapper.decide(candidateNo, "DEFERRED", c.getEnforcementState(),
                null, cal.getTime(), null, memberNo);
    }

    /**
     * 확정된 Candidate 를 엔진이 실제로 읽는 Rule 로 내린다.
     *
     * 개별쌍은 DB 제약이 작은 번호를 trigger 로 강제하므로 뒤집어 넣는다.
     * 방향 정보는 Candidate 에 남아 있으므로 잃지 않는다.
     */
    @Override
    public Map<String, Integer> publishRules() {

        List<ConflictCandidateVO> list = mapper.findConfirmedForRule();
        int pair = 0, external = 0;

        for (ConflictCandidateVO c : list) {

            String ruleText = ConflictRuleTextBuilder.build(c);
            String type = ConflictRuleTextBuilder.toConflictType(c);

            if (c.getMappedBenefitNo() == null) {
                // trigger 가 NULL 이면 UNIQUE 가 걸리지 않으므로 직접 확인한다
                if (mapper.countExternalRule(c.getSourceBenefitNo(), ruleText) > 0) {
                    continue;
                }
                if (mapper.insertRule(null, c.getSourceBenefitNo(), "확인필요", ruleText) > 0) {
                    external++;
                }
                continue;
            }

            int a = Math.min(c.getSourceBenefitNo(), c.getMappedBenefitNo());
            int b = Math.max(c.getSourceBenefitNo(), c.getMappedBenefitNo());
            if (mapper.insertRule(a, b, type, ruleText) > 0) {
                pair++;
            }
        }

        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("candidate", list.size());
        out.put("pairRule", pair);
        out.put("externalWarning", external);
        System.out.println("[Rule 생성] " + out);
        return out;
    }

    @Override
    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>(mapper.summaryCandidate());
        out.put("total_benefit", mapper.countBenefit());
        return out;
    }
}