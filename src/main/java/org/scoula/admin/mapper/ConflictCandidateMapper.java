package org.scoula.admin.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.scoula.admin.domain.ConflictCandidateVO;

import java.util.List;
import java.util.Map;

public interface ConflictCandidateMapper {

    // ------------------------------------------------------------
    // 저장
    // ------------------------------------------------------------

    /**
     * 같은 의미가 두 번 들어오면 조용히 무시한다.
     * uk_candidate_dedupe(dedupe_key, prompt_version, source_text_hash) 로 막는데,
     * 프롬프트나 원문이 바뀌면 새 행이 생겨야 이전 결과와 비교할 수 있으므로
     * dedupe_key 단독 UNIQUE 로 하지 않았다.
     */
    @Insert("INSERT IGNORE INTO benefit_conflict_candidate (" +
            " source_benefit_no, scope, relation," +
            " target_name_raw, target_category_raw, category_code," +
            " direction, timing, subject_scope, restriction_stage," +
            " combination_applicability, trigger_scope," +
            " condition_type, condition_text," +
            " evidence_text, evidence_verified, confidence," +
            " mapped_benefit_no, resolver_result, resolver_candidates," +
            " verifier_verdict, blocking_reasons, crosscheck_result," +
            " analysis_status, workflow_status, enforcement_state, review_reason," +
            " model_name, prompt_key, prompt_version, source_text_hash, dedupe_key" +
            ") VALUES (" +
            " #{sourceBenefitNo}, #{scope}, #{relation}," +
            " #{targetNameRaw}, #{targetCategoryRaw}, #{categoryCode}," +
            " #{direction}, #{timing}, #{subjectScope}, #{restrictionStage}," +
            " #{combinationApplicability}, #{triggerScope}," +
            " #{conditionType}, #{conditionText}," +
            " #{evidenceText}, #{evidenceVerified}, #{confidence}," +
            " #{mappedBenefitNo}, #{resolverResult}, #{resolverCandidates}," +
            " #{verifierVerdict}, #{blockingReasons}, #{crosscheckResult}," +
            " #{analysisStatus}, #{workflowStatus}, #{enforcementState}, #{reviewReason}," +
            " #{modelName}, #{promptKey}, #{promptVersion}, #{sourceTextHash}, #{dedupeKey})")
    @Options(useGeneratedKeys = true, keyProperty = "candidateNo")
    int insertCandidate(ConflictCandidateVO vo);

    @Select("SELECT COUNT(*) FROM benefit_conflict_candidate")
    int countAll();

    @Select("SELECT scope, COUNT(*) AS cnt FROM benefit_conflict_candidate " +
            "GROUP BY scope ORDER BY cnt DESC")
    List<Map<String, Object>> countByScope();

    /** 재분석 전 초기화용. 관리자 판정이 들어간 뒤에는 절대 쓰지 말 것 */
    @Delete("DELETE FROM benefit_conflict_candidate " +
            "WHERE workflow_status = 'UNRESOLVED' AND prompt_version = #{promptVersion}")
    int deleteUnresolvedByVersion(@Param("promptVersion") Integer promptVersion);

    // ------------------------------------------------------------
    // Cross-check
    // ------------------------------------------------------------

    /** Cross-check 대상. 우리 DB 정책으로 연결됐고 근거도 확인된 것만 */
    @Select("SELECT candidate_no, source_benefit_no, mapped_benefit_no, direction, " +
            "       relation, combination_applicability, target_name_raw " +
            "FROM benefit_conflict_candidate " +
            "WHERE resolver_result = 'UNIQUE_MATCH' AND evidence_verified = 'Y' " +
            "  AND workflow_status = 'UNRESOLVED' " +
            "ORDER BY candidate_no")
    List<ConflictCandidateVO> findCrosscheckTargets();

    /**
     * 상대 정책이 나를 되짚었는가.
     * 상대 공고문이 독립적으로 같은 관계를 말했다면 양방향 근거가 된다.
     * AI 한 쪽만 보고는 절대 알 수 없는 정보다.
     */
    @Select("SELECT relation FROM benefit_conflict_candidate " +
            "WHERE source_benefit_no = #{mappedNo} AND mapped_benefit_no = #{sourceNo} " +
            "  AND evidence_verified = 'Y' LIMIT 1")
    String findCounterpartRelation(@Param("sourceNo") int sourceNo,
                                   @Param("mappedNo") int mappedNo);

    @Select("SELECT COUNT(*) FROM benefit_conflict_candidate WHERE source_benefit_no = #{benefitNo}")
    int countBySource(@Param("benefitNo") int benefitNo);

    @Update("UPDATE benefit_conflict_candidate SET crosscheck_result = #{result}, " +
            "       direction = #{direction} WHERE candidate_no = #{candidateNo}")
    int updateCrosscheck(@Param("candidateNo") int candidateNo,
                         @Param("result") String result,
                         @Param("direction") String direction);

    /** 상호 확인된 관계는 한쪽의 강한 근거를 반대편에도 적용한다 */
    @Update("UPDATE benefit_conflict_candidate SET combination_applicability = 'YES' " +
            "WHERE source_benefit_no = #{sourceNo} AND mapped_benefit_no = #{mappedNo} " +
            "  AND combination_applicability <> 'YES'")
    int propagateApplicability(@Param("sourceNo") int sourceNo,
                               @Param("mappedNo") int mappedNo);

    // ------------------------------------------------------------
    // Gate
    // ------------------------------------------------------------

    @Select("SELECT candidate_no, source_benefit_no, mapped_benefit_no, target_name_raw," +
            "       target_category_raw, relation, direction, timing, subject_scope," +
            "       restriction_stage, combination_applicability, condition_type," +
            "       evidence_verified, resolver_result, crosscheck_result " +
            "FROM benefit_conflict_candidate WHERE workflow_status = 'UNRESOLVED'")
    List<ConflictCandidateVO> findUnresolved();

    /**
     * 폐기 이유도 함께 저장한다.
     * 왜 버려졌는지 남지 않으면 나중에 같은 후보가 또 떴을 때 판단 근거가 없다.
     */
    @Update("UPDATE benefit_conflict_candidate SET " +
            "  workflow_status = #{workflowStatus}, enforcement_state = #{enforcementState}," +
            "  review_reason = #{reviewReason}, discard_reason = #{discardReason} " +
            "WHERE candidate_no = #{candidateNo}")
    int updateGate(ConflictCandidateVO vo);

    @Select("SELECT workflow_status, enforcement_state, review_reason, COUNT(*) AS cnt " +
            "FROM benefit_conflict_candidate " +
            "GROUP BY workflow_status, enforcement_state, review_reason ORDER BY cnt DESC")
    List<Map<String, Object>> gateSummary();

    @Select("SELECT plcy_nm FROM benefit WHERE benefit_no = #{benefitNo}")
    String findPlcyNm(@Param("benefitNo") int benefitNo);

    // ------------------------------------------------------------
    // 동기화 재해소
    // ------------------------------------------------------------

    /**
     * 이 정책을 이 프롬프트 버전·이 본문으로 이미 분석했는가.
     *
     * 매일 동기화가 도는데 149건을 매번 다시 태우면 비용도 시간도 낭비다.
     * 본문이 그대로면 결과도 같으므로 건너뛴다.
     */
    @Select("SELECT COUNT(*) FROM benefit_conflict_candidate " +
            "WHERE source_benefit_no = #{benefitNo} " +
            "  AND source_text_hash = #{hash} " +
            "  AND prompt_version = #{promptVersion}")
    int countAnalyzed(@Param("benefitNo") int benefitNo,
                      @Param("hash") String hash,
                      @Param("promptVersion") Integer promptVersion);

    /**
     * 다시 해소를 시도할 대상.
     *
     * 후보가 여럿이라 대기 중인 건과, DB 에 없어 안내로만 처리한 건 둘 다 본다.
     * 새 정책이 들어오거나 중복 적재가 정리되면 하나로 좁혀질 수 있고,
     * 그러면 안내가 아니라 실제 개별쌍 규칙이 될 수 있다.
     *
     * 관리자가 이미 판정한 건은 건드리지 않는다.
     */
    @Select("SELECT candidate_no, source_benefit_no, target_name_raw, " +
            "       resolver_result, resolve_retry_cnt " +
            "FROM benefit_conflict_candidate " +
            "WHERE target_name_raw IS NOT NULL " +
            "  AND mapped_benefit_no IS NULL " +
            "  AND evidence_verified = 'Y' " +
            "  AND resolve_retry_cnt < 30 " +
            "  AND decided_by IS NULL " +
            "  AND ( workflow_status = 'PENDING_DATA' " +
            "     OR (workflow_status = 'CONFIRMED' AND enforcement_state = 'WARNING') ) " +
            "ORDER BY candidate_no")
    List<ConflictCandidateVO> findResolvableAgain();

    @Update("UPDATE benefit_conflict_candidate SET " +
            "  mapped_benefit_no = #{mappedNo}, resolver_result = #{result}," +
            "  resolver_candidates = #{candidates}," +
            "  resolve_retry_cnt = resolve_retry_cnt + 1, last_resolved_at = NOW()," +
            "  workflow_status = #{workflowStatus}, review_reason = #{reviewReason} " +
            "WHERE candidate_no = #{candidateNo}")
    int updateResolve(@Param("candidateNo") int candidateNo,
                      @Param("mappedNo") Integer mappedNo,
                      @Param("result") String result,
                      @Param("candidates") String candidates,
                      @Param("workflowStatus") String workflowStatus,
                      @Param("reviewReason") String reviewReason);

    @Update("UPDATE benefit_conflict_candidate SET " +
            "  resolve_retry_cnt = resolve_retry_cnt + 1, last_resolved_at = NOW() " +
            "WHERE candidate_no = #{candidateNo}")
    int touchResolve(@Param("candidateNo") int candidateNo);

    @Select("SELECT workflow_status, COUNT(*) AS cnt FROM benefit_conflict_candidate " +
            "GROUP BY workflow_status")
    List<Map<String, Object>> countByWorkflow();

    // ------------------------------------------------------------
    // 검수
    // ------------------------------------------------------------

    /**
     * 검수 대기 목록.
     *
     * A 와 B 가 이미 특정된 것만 여기 온다.
     * 정렬은 confidence 가 아니라 사용자 영향도다.
     */
    @Select("SELECT c.candidate_no, c.source_benefit_no, c.mapped_benefit_no," +
            "       c.target_name_raw, c.target_category_raw, c.relation," +
            "       c.direction, c.timing, c.subject_scope, c.restriction_stage," +
            "       c.combination_applicability, c.condition_type, c.condition_text," +
            "       c.evidence_text, c.evidence_verified, c.confidence," +
            "       c.resolver_result, c.resolver_candidates, c.crosscheck_result," +
            "       c.workflow_status, c.enforcement_state, c.review_reason," +
            "       c.deferred_until," +
            "       b1.plcy_nm AS source_plcy_nm, b1.sprvsn_inst_cd_nm AS source_inst," +
            "       b2.plcy_nm AS mapped_plcy_nm, b2.sprvsn_inst_cd_nm AS mapped_inst," +
            "       b2.is_active AS mapped_active " +
            "FROM benefit_conflict_candidate c " +
            "JOIN benefit b1 ON b1.benefit_no = c.source_benefit_no " +
            "LEFT JOIN benefit b2 ON b2.benefit_no = c.mapped_benefit_no " +
            "WHERE c.workflow_status IN ('REVIEW_REQUIRED','DEFERRED') " +
            "  AND (c.deferred_until IS NULL OR c.deferred_until <= NOW()) " +
            "ORDER BY FIELD(c.enforcement_state,'PENDING_BLOCK') DESC, " +
            "         FIELD(c.review_reason,'RELATION_CHECK','DIRECTION_UNKNOWN'," +
            "               'COMBINATION_APPLICABILITY_UNKNOWN','CONDITIONAL'," +
            "               'CONTRADICTORY_EVIDENCE','EXTRACTION_INVALID'), " +
            "         c.candidate_no")
    List<Map<String, Object>> findReviewQueue();

    @Select("SELECT c.*, b1.plcy_nm AS source_plcy_nm, b1.sprvsn_inst_cd_nm AS source_inst, " +
            "       b2.plcy_nm AS mapped_plcy_nm, b2.sprvsn_inst_cd_nm AS mapped_inst " +
            "FROM benefit_conflict_candidate c " +
            "JOIN benefit b1 ON b1.benefit_no = c.source_benefit_no " +
            "LEFT JOIN benefit b2 ON b2.benefit_no = c.mapped_benefit_no " +
            "WHERE c.candidate_no = #{candidateNo}")
    Map<String, Object> findReviewDetail(@Param("candidateNo") int candidateNo);

    @Select("SELECT candidate_no, source_benefit_no, mapped_benefit_no, target_name_raw," +
            "       target_category_raw, relation, direction, timing, subject_scope," +
            "       restriction_stage, condition_type, evidence_text," +
            "       enforcement_state, workflow_status " +
            "FROM benefit_conflict_candidate WHERE candidate_no = #{candidateNo}")
    ConflictCandidateVO findByNo(@Param("candidateNo") int candidateNo);

    /** 후보 정책의 이름과 기관. 번호만 보여주면 관리자가 아무것도 고를 수 없다 */
    @Select({"<script>",
            "SELECT benefit_no, plcy_nm, sprvsn_inst_cd_nm, is_active",
            "FROM benefit WHERE benefit_no IN",
            "<foreach collection='nos' item='no' open='(' separator=',' close=')'>#{no}</foreach>",
            "ORDER BY FIELD(is_active,'Y') DESC, benefit_no",
            "</script>"})
    List<Map<String, Object>> findBenefitBriefs(@Param("nos") List<Integer> nos);

    @Update("UPDATE benefit_conflict_candidate SET mapped_benefit_no = #{mappedNo}, " +
            "       resolver_result = 'UNIQUE_MATCH' WHERE candidate_no = #{candidateNo}")
    int fixMapping(@Param("candidateNo") int candidateNo, @Param("mappedNo") int mappedNo);

    @Update("UPDATE benefit_conflict_candidate SET " +
            "  workflow_status = #{status}, enforcement_state = #{enforcement}," +
            "  conflict_decision = #{decision}," +
            "  deferred_until = #{deferredUntil}, discard_reason = #{discardReason}," +
            "  decided_by = #{memberNo}, decided_at = NOW() " +
            "WHERE candidate_no = #{candidateNo}")
    int decide(@Param("candidateNo") int candidateNo,
               @Param("status") String status,
               @Param("enforcement") String enforcement,
               @Param("decision") String decision,
               @Param("deferredUntil") java.util.Date deferredUntil,
               @Param("discardReason") String discardReason,
               @Param("memberNo") Integer memberNo);

    // ------------------------------------------------------------
    // Rule 생성
    // ------------------------------------------------------------

    /** 엔진이 실제로 쓰는 Rule 로 내린다. 이미 있으면 무시한다 */
    @Insert("INSERT IGNORE INTO benefit_conflict_rule " +
            " (trigger_benefit_no, target_benefit_no, conflict_type, rule_text," +
            "  detection_type, confirm_status, is_active, created_at, updated_at) " +
            "VALUES (#{triggerNo}, #{targetNo}, #{conflictType}, #{ruleText}," +
            "        '키워드자동탐지', '확정', 'Y', NOW(), NOW())")
    int insertRule(@Param("triggerNo") Integer triggerNo,
                   @Param("targetNo") Integer targetNo,
                   @Param("conflictType") String conflictType,
                   @Param("ruleText") String ruleText);

    /** 자동 확정분과 관리자 확정분을 Rule 로 내릴 대상 */
    @Select("SELECT candidate_no, source_benefit_no, mapped_benefit_no, target_name_raw," +
            "       target_category_raw, relation, timing, subject_scope, restriction_stage," +
            "       enforcement_state, condition_type, conflict_decision, direction " +
            "FROM benefit_conflict_candidate " +
            "WHERE workflow_status = 'CONFIRMED' " +
            "  AND enforcement_state IN ('CONFIRMED_BLOCK','WARNING')")
    List<ConflictCandidateVO> findConfirmedForRule();

    /** 요약 화면용. 관리자가 전체 그림을 먼저 보게 한다 */
    @Select("SELECT " +
            " (SELECT COUNT(*) FROM benefit) AS total_benefit," +
            " (SELECT COUNT(*) FROM benefit_conflict_candidate) AS candidate," +
            " SUM(workflow_status='CONFIRMED' AND enforcement_state='WARNING') AS auto_warning," +
            " SUM(enforcement_state='CONFIRMED_BLOCK') AS auto_rule," +
            " SUM(workflow_status='DISCARDED') AS discarded," +
            " SUM(workflow_status='PENDING_DATA') AS pending_data," +
            " SUM(workflow_status IN ('REVIEW_REQUIRED','DEFERRED')) AS review," +
            " SUM(workflow_status='DEFERRED') AS deferred " +
            "FROM benefit_conflict_candidate")
    Map<String, Object> summary();
}