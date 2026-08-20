package org.scoula.admin.service;

import java.util.List;
import java.util.Map;

public interface ConflictReviewService {

    /** 검수 대기 목록. A 와 B 가 이미 특정된 것만 온다 */
    List<Map<String, Object>> queue();

    /** 보류 중인 건. 만료 전에도 조회하고 판정할 수 있다 */
    List<Map<String, Object>> deferredQueue();

    /** 전체 그림. 관리자가 무엇을 몇 건 하는지 먼저 본다 */
    Map<String, Object> summary();

    Map<String, Object> detail(int candidateNo);

    /**
     * 관리자 판정.
     * decision — BLOCK(함께 받을 수 없음) / PARTIAL(함께 받되 제한) / NOT_CONFLICT(관계 아님)
     */
    void decide(int candidateNo, String decision, Integer mappedNo,
                String reason, Integer memberNo);

    /** 판단 보류. 잠금은 유지하고 보류 목록에서 다시 볼 수 있다 */
    void defer(int candidateNo, int days, Integer memberNo);

    /** 확정분을 benefit_conflict_rule 로 내린다 */
    Map<String, Integer> publishRules();
}