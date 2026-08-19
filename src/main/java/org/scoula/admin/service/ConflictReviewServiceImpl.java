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

    /**
     * 보류 중인 건.
     *
     * 검수 목록은 만료일이 지난 건만 가져오므로
     * 보류한 뒤 판단이 바뀌어도 만료 전까지 다시 볼 방법이 없었다.
     * 보류는 판단을 미루는 것이지 잠그는 것이 아니므로 언제든 열람할 수 있어야 한다.
     */
    @Override
    public List<Map<String, Object>> deferredQueue() {
        return mapper.findDeferredQueue();
    }

    @Override
    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>(mapper.summaryCandidate());
        out.put("total_benefit", mapper.countBenefit());
        return out;
    }

    @Override
    public Map<String, Object> detail(int candidateNo) {
        return mapper.findReviewDetail(candidateNo);
    }

    /**
     * 관리자 판정.
     *
     * decision 은 관리자가 누른 버튼이고, 그것이 곧 엔진 동작은 아니다.
     * "함께 받을 수 없음" 을 눌러도 한쪽 공고에만 근거가 있으면
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
     * 개별쌍으로 저장하는 조건은 상대 정책 지정만으로 부족하다.
     * 우리 DB 의 개별쌍은 방향을 담을 자리가 없어 항상 대칭으로 동작하므로,
     * 한쪽 공고에만 근거가 있는 관계를 개별쌍으로 저장하면
     * 반대편 사용자가 받을 수 있었던 정책을 잃는다.
     * 그래서 조합 제외가 확정된 건만 개별쌍으로 만든다.
     */
    @Override
    public Map<String, Integer> publishRules() {

        List<ConflictCandidateVO> list = mapper.findConfirmedForRule();
        int pair = 0, external = 0;

        for (ConflictCandidateVO c : list) {

            String ruleText = ConflictRuleTextBuilder.build(c);
            String type = ConflictRuleTextBuilder.toConflictType(c);

            boolean asPair = c.getMappedBenefitNo() != null
                    && "CONFIRMED_BLOCK".equals(c.getEnforcementState());

            if (!asPair) {
                // trigger 가 NULL 이면 MySQL 이 UNIQUE 중복을 허용하므로
                // 저장 전에 같은 내용의 규칙이 있는지 직접 확인한다
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
}