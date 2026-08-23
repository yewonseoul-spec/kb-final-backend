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

    /**
     * Cross-check 대상. 우리 DB 정책으로 연결됐고 근거도 확인된 것만.
     *
     * 세대를 지정해서 가져온다.
     * 상호 대조는 같은 프롬프트로 분석한 것끼리 해야 의미가 있는데,
     * 세대를 가리지 않으면 예전 분석 결과가 근거로 섞여 들어온다.
     * 낡음으로 표시된 행도 현재 실행에서는 쓰지 않는다.
     */
    @Select("SELECT candidate_no, source_benefit_no, mapped_benefit_no, direction, " +
            "       relation, combination_applicability, target_name_raw " +
            "FROM benefit_conflict_candidate " +
            "WHERE resolver_result = 'UNIQUE_MATCH' AND evidence_verified = 'Y' " +
            "  AND workflow_status = 'UNRESOLVED' " +
            "  AND analysis_status = 'SUCCESS' " +
            "  AND prompt_version = #{promptVersion} " +
            "ORDER BY candidate_no")
    List<ConflictCandidateVO> findCrosscheckTargets(@Param("promptVersion") Integer promptVersion);

    /**
     * 한 공고가 상대 하나에 대해 말한 근거를 전부 가져온다.
     *
     * 예전에는 관계 하나만 LIMIT 1 로 뽑았는데,
     * 한 공고가 같은 상대에 대해 "원칙은 불가, 다만 이 경우는 가능" 처럼
     * 여러 관계를 말할 수 있어 어느 것이 뽑힐지 정해지지 않았다.
     *
     * 관계 종류로 미리 좁히지 않는다.
     * 금지 근거만 골라 오면 같은 공고에 있는 허용 근거를 못 보게 되고,
     * 그러면 상충하는 상태를 상호 확인으로 잘못 읽는다.
     * 무엇을 쓸지는 가져온 뒤 자바에서 정한다.
     *
     * from 이 말한 to 에 대한 근거를 찾는다.
     * 반대편 근거가 필요하면 두 번호를 바꿔 부르면 된다.
     */
    @Select("SELECT candidate_no, source_benefit_no, mapped_benefit_no, relation," +
            "       workflow_status, review_reason, evidence_verified, resolver_result," +
            "       condition_type, combination_applicability, crosscheck_result," +
            "       analysis_status, prompt_version " +
            "FROM benefit_conflict_candidate " +
            "WHERE source_benefit_no = #{fromBenefitNo} " +
            "  AND mapped_benefit_no = #{toBenefitNo} " +
            "  AND analysis_status = 'SUCCESS' " +
            "  AND prompt_version = #{promptVersion} " +
            "  AND evidence_verified = 'Y' " +
            "ORDER BY candidate_no")
    List<ConflictCandidateVO> findPairEvidence(@Param("fromBenefitNo") int fromBenefitNo,
                                               @Param("toBenefitNo") int toBenefitNo,
                                               @Param("promptVersion") Integer promptVersion);

    @Update("UPDATE benefit_conflict_candidate SET crosscheck_result = #{result}, " +
            "       direction = #{direction} WHERE candidate_no = #{candidateNo}")
    int updateCrosscheck(@Param("candidateNo") int candidateNo,
                         @Param("result") String result,
                         @Param("direction") String direction);

    // combination_applicability 를 반대편 Candidate 에 옮겨 적던 propagateApplicability 는
    // 제거했다. 그 값은 각 공고문이 스스로 무엇을 말했는지를 담는 자리인데,
    // 상호 확인이 됐다는 이유로 반대편 값을 덮어쓰면
    // 그 공고가 실제로 한 말이 사라진다.
    // 개별쌍 규칙은 한쪽 Candidate 가 확정되면 만들어지므로 옮겨 적을 이유도 없다.

    // ------------------------------------------------------------
    // Gate
    // ------------------------------------------------------------

    /** 판정 대상. 지정한 세대의 유효한 분석만 본다 */
    @Select("SELECT candidate_no, source_benefit_no, mapped_benefit_no, target_name_raw," +
            "       target_category_raw, relation, direction, timing, subject_scope," +
            "       restriction_stage, combination_applicability, condition_type," +
            "       evidence_verified, resolver_result, crosscheck_result " +
            "FROM benefit_conflict_candidate " +
            "WHERE workflow_status = 'UNRESOLVED' " +
            "  AND analysis_status = 'SUCCESS' " +
            "  AND prompt_version = #{promptVersion}")
    List<ConflictCandidateVO> findUnresolved(@Param("promptVersion") Integer promptVersion);

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
            "  AND analysis_status = 'SUCCESS' " +
            "  AND prompt_version = #{promptVersion} " +
            "  AND ( workflow_status = 'PENDING_DATA' " +
            "     OR (workflow_status = 'CONFIRMED' AND enforcement_state = 'WARNING') ) " +
            "ORDER BY candidate_no")
    List<ConflictCandidateVO> findResolvableAgain(@Param("promptVersion") Integer promptVersion);

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
            "WHERE c.workflow_status = 'REVIEW_REQUIRED' " +
            "  AND c.analysis_status = 'SUCCESS' " +
            "  AND c.prompt_version = #{promptVersion} " +
            "ORDER BY FIELD(c.enforcement_state,'PENDING_BLOCK') DESC, " +
            "         FIELD(c.review_reason,'RELATION_CHECK','DIRECTION_UNKNOWN'," +
            "               'COMBINATION_APPLICABILITY_UNKNOWN','CONDITIONAL'," +
            "               'CONTRADICTORY_EVIDENCE','EXTRACTION_INVALID'), " +
            "         c.candidate_no")
    List<Map<String, Object>> findReviewQueue(@Param("promptVersion") Integer promptVersion);

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

    /**
     * 이 안내 규칙이 이미 있는가.
     *
     * MySQL 은 UNIQUE 에서 NULL 중복을 허용하므로
     * trigger 가 NULL 인 범주·외부 안내는 INSERT IGNORE 로 막히지 않는다.
     * 실제로 publish 를 두 번 눌렀더니 54건이 108건이 됐다.
     */
    @Select("SELECT COUNT(*) FROM benefit_conflict_rule " +
            "WHERE trigger_benefit_no IS NULL " +
            "  AND target_benefit_no = #{targetNo} " +
            "  AND rule_text = #{ruleText}")
    int countExternalRule(@Param("targetNo") int targetNo,
                          @Param("ruleText") String ruleText);

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

    /**
     * 요약 화면용.
     *
     * 서브쿼리와 집계를 한 SELECT 에 섞으면
     * ONLY_FULL_GROUP_BY 설정에서 거부되는 경우가 있어 나눠 조회한다.
     */
    @Select("SELECT " +
            " COUNT(*) AS candidate," +
            " SUM(workflow_status = 'CONFIRMED' AND enforcement_state = 'WARNING') AS auto_warning," +
            " SUM(enforcement_state = 'CONFIRMED_BLOCK') AS auto_rule," +
            " SUM(workflow_status = 'DISCARDED') AS discarded," +
            " SUM(workflow_status = 'PENDING_DATA') AS pending_data," +
            " SUM(workflow_status IN ('REVIEW_REQUIRED','DEFERRED')) AS review," +
            " SUM(workflow_status = 'DEFERRED') AS deferred " +
            "FROM benefit_conflict_candidate")
    Map<String, Object> summaryCandidate();

    @Select("SELECT COUNT(*) FROM benefit")
    int countBenefit();

    /**
     * 보류 중인 건.
     *
     * 목록 조회는 만료일이 지난 건만 가져오므로
     * 보류한 뒤 판단이 바뀌어도 만료 전까지 다시 볼 방법이 없었다.
     * 보류는 판단을 미루는 것이지 잠그는 것이 아니므로 언제든 열람할 수 있어야 한다.
     */
    @Select("SELECT c.candidate_no, c.source_benefit_no, c.mapped_benefit_no," +
            "       c.target_name_raw, c.target_category_raw, c.relation," +
            "       c.direction, c.timing, c.subject_scope, c.restriction_stage," +
            "       c.combination_applicability, c.condition_type, c.condition_text," +
            "       c.evidence_text, c.evidence_verified, c.confidence," +
            "       c.resolver_result, c.resolver_candidates, c.crosscheck_result," +
            "       c.workflow_status, c.enforcement_state, c.review_reason," +
            "       c.deferred_until, c.decided_at," +
            "       b1.plcy_nm AS source_plcy_nm, b1.sprvsn_inst_cd_nm AS source_inst," +
            "       b2.plcy_nm AS mapped_plcy_nm, b2.sprvsn_inst_cd_nm AS mapped_inst," +
            "       b2.is_active AS mapped_active " +
            "FROM benefit_conflict_candidate c " +
            "JOIN benefit b1 ON b1.benefit_no = c.source_benefit_no " +
            "LEFT JOIN benefit b2 ON b2.benefit_no = c.mapped_benefit_no " +
            "WHERE c.workflow_status = 'DEFERRED' " +
            "ORDER BY c.deferred_until, c.candidate_no")
    List<Map<String, Object>> findDeferredQueue();
}