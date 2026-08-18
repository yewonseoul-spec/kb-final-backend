package org.scoula.admin.service;

import java.util.List;
import java.util.Map;

public interface ConflictReviewService {

    List<Map<String, Object>> queue();

    Map<String, Object> detail(int candidateNo);

    /**
     * 관리자 판정.
     * decision — BLOCK(중복 불가) / PARTIAL(금액 조정) / NOT_CONFLICT(관계 아님)
     */
    void decide(int candidateNo, String decision, Integer mappedNo,
                String reason, Integer memberNo);

    /** 판단 보류. 잠금은 유지하고 만료 후 다시 표시한다 */
    void defer(int candidateNo, int days, Integer memberNo);

    /** 확정분을 benefit_conflict_rule 로 내린다 */
    Map<String, Integer> publishRules();

    Map<String, Object> summary();
}