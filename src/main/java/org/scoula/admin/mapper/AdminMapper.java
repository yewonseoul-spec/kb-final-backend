package org.scoula.admin.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.scoula.admin.domain.SyncLogDetailVO;
import org.scoula.admin.domain.SyncLogVO;
import org.scoula.admin.dto.AdminBenefitDetailResDto;
import org.scoula.admin.dto.AdminBenefitListResDto;
import org.scoula.admin.dto.AdminBenefitSearchReqDto;
import org.scoula.admin.dto.DeadlineBenefitResDto;
import org.scoula.admin.dto.SyncLogDetailResDto;
import org.scoula.admin.dto.SyncLogSearchReqDto;
import org.scoula.admin.dto.SyncLogStatsResDto;

import java.util.List;

/**
 * 관리자 조회·기록 매퍼
 *
 * 혜택의 노출 상태는 세 컬럼이 겹쳐 있으므로 조회할 때 하나로 합친다.
 *   api_deleted_yn   오픈 API 에서 사라진 정책. 소프트 딜리트라 행은 남아 있다
 *   admin_is_active  관리자가 지정한 상태. 동기화가 덮지 않는다
 *   is_active        API 원본 상태. 동기화가 매번 덮어쓴다
 *
 * 우선순위는 위에서부터다. API 에서 사라진 정책은 관리자가 활성으로 켜도
 * 신청할 곳이 없으므로 가장 앞에 둔다.
 */
public interface AdminMapper {

    // ==================================================================
    // admin-01 : 대시보드
    // ==================================================================

    // 동기화 전후 비교용 정책 총 건수 (대시보드 '전체 정책' 카드와 공용)
    // 숨김 처리된 정책도 DB 에는 남아 있으므로 여기서는 세지 않는다
    @Select("SELECT COUNT(*) FROM benefit WHERE api_deleted_yn = 'N'")
    int countBenefits();

    // 추천 가능 정책 수. 마감·미도래를 뺀 실제 추천 대상
    // 관리자가 끈 정책은 추천에서 빠지므로 최종 상태로 센다
    @Select("SELECT COUNT(*) FROM benefit " +
            "WHERE api_deleted_yn = 'N' " +
            "  AND COALESCE(admin_is_active, is_active) = 'Y'")
    int countActiveBenefits();

    // 오픈 API 에서 사라져 숨김 처리된 정책 수
    @Select("SELECT COUNT(*) FROM benefit WHERE api_deleted_yn = 'Y'")
    int countDeletedBenefits();

    // 30일 이내 마감 정책 수. 상시모집(apply_end_date NULL)은 제외
    @Select("SELECT COUNT(*) FROM benefit " +
            "WHERE api_deleted_yn = 'N' " +
            "  AND COALESCE(admin_is_active, is_active) = 'Y' " +
            "  AND apply_end_date IS NOT NULL " +
            "  AND apply_end_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)")
    int countDeadlineSoon();

    // 엔진이 실제로 적용하는 중복수혜 규칙 수 (검수 전·비활성 제외)
    @Select("SELECT COUNT(*) FROM benefit_conflict_rule " +
            "WHERE confirm_status = '확정' AND is_active = 'Y'")
    int countActiveConflictRules();

    // 탈퇴하지 않은 회원 수
    @Select("SELECT COUNT(*) FROM member WHERE status = 'Y'")
    int countMembers();

    // 최근 동기화 이력.
    // log_no 순서와 실행 시각 순서가 어긋난 데이터가 있어 executed_at을 1순위로 둔다.
    @Select("SELECT log_no, executed_at, exec_type, sync_start_date, sync_end_date, " +
            "       result_status, total_cnt, insert_cnt, update_cnt, skip_cnt, " +
            "       error_msg, duration_ms, member_no " +
            "FROM sync_log " +
            "ORDER BY executed_at DESC, log_no DESC " +
            "LIMIT #{limit}")
    List<SyncLogVO> findRecentSyncLogs(@Param("limit") int limit);

    // 마감 임박 정책 목록
    @Select("SELECT benefit_no, plcy_nm, category_code, apply_end_date, " +
            "       DATEDIFF(apply_end_date, CURDATE()) AS dday " +
            "FROM benefit " +
            "WHERE api_deleted_yn = 'N' " +
            "  AND COALESCE(admin_is_active, is_active) = 'Y' " +
            "  AND apply_end_date IS NOT NULL " +
            "  AND apply_end_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY) " +
            "ORDER BY apply_end_date ASC, benefit_no ASC " +
            "LIMIT #{limit}")
    List<DeadlineBenefitResDto> findDeadlineSoonBenefits(@Param("limit") int limit);

    // 동기화 실행 이력 기록
    @Insert("INSERT INTO sync_log (" +
            "executed_at, exec_type, sync_start_date, sync_end_date, result_status, total_cnt, " +
            "insert_cnt, update_cnt, skip_cnt, error_msg, duration_ms, member_no" +
            ") VALUES (" +
            "NOW(), #{execType}, #{syncStartDate}, #{syncEndDate}, #{resultStatus}, #{totalCnt}, " +
            "#{insertCnt}, #{updateCnt}, #{skipCnt}, #{errorMsg}, #{durationMs}, #{memberNo}" +
            ")")
    @Options(useGeneratedKeys = true, keyProperty = "logNo")
    int insertSyncLog(SyncLogVO syncLog);


    // ==================================================================
    // admin-03 : 동기화 로그 목록
    // ==================================================================
    // 조건이 동적이라 <script>를 쓴다. XML로 빼지 않는 이유는
    // RootConfig의 mapperLocations에 admin 경로가 없어 XML을 추가하려면
    // 설정 변경이 따르고, 예전에 그 경로 문제로 시간을 크게 잃은 적이 있기 때문이다.
    //
    // <script> 안은 XML로 파싱되므로 '<'는 그대로 쓸 수 없다.
    // 부등호는 좌우를 바꿔 '>='로 표현해 이스케이프를 피한다.

    /** 조건에 맞는 동기화 이력 목록 (페이지네이션) */
    @Select("<script>"
            + "SELECT log_no, executed_at, exec_type, sync_start_date, sync_end_date, "
            + "       result_status, total_cnt, insert_cnt, update_cnt, skip_cnt, "
            + "       error_msg, duration_ms, member_no "
            + "FROM sync_log "
            + "<where>"
            + "  <if test='startDate != null and startDate != \"\"'>"
            + "    AND executed_at >= CONCAT(#{startDate}, ' 00:00:00')"
            + "  </if>"
            + "  <if test='endDate != null and endDate != \"\"'>"
            + "    AND CONCAT(#{endDate}, ' 23:59:59') >= executed_at"
            + "  </if>"
            + "  <if test='resultStatus != null and resultStatus != \"\"'>"
            + "    AND result_status = #{resultStatus}"
            + "  </if>"
            + "  <if test='execType != null and execType != \"\"'>"
            + "    AND exec_type = #{execType}"
            + "  </if>"
            + "</where>"
            + "ORDER BY executed_at DESC, log_no DESC "
            + "LIMIT #{offset}, #{size}"
            + "</script>")
    List<SyncLogVO> findSyncLogs(SyncLogSearchReqDto search);

    /** 목록과 같은 조건의 전체 건수 (페이지 수 계산용) */
    @Select("<script>"
            + "SELECT COUNT(*) FROM sync_log "
            + "<where>"
            + "  <if test='startDate != null and startDate != \"\"'>"
            + "    AND executed_at >= CONCAT(#{startDate}, ' 00:00:00')"
            + "  </if>"
            + "  <if test='endDate != null and endDate != \"\"'>"
            + "    AND CONCAT(#{endDate}, ' 23:59:59') >= executed_at"
            + "  </if>"
            + "  <if test='resultStatus != null and resultStatus != \"\"'>"
            + "    AND result_status = #{resultStatus}"
            + "  </if>"
            + "  <if test='execType != null and execType != \"\"'>"
            + "    AND exec_type = #{execType}"
            + "  </if>"
            + "</where>"
            + "</script>")
    int countSyncLogs(SyncLogSearchReqDto search);

    /**
     * 상단 통계 카드용 집계.
     * 상태·유형 필터는 일부러 적용하지 않는다.
     * SUCCESS만 걸러놓고 '성공 41회 실패 0회'를 보여주면 정보가 사라지기 때문에,
     * 기간 안의 전체 그림(총 몇 회 중 몇 번 실패)을 유지한다.
     */
    @Select("<script>"
            + "SELECT COUNT(*) AS totalCount, "
            + "       COALESCE(SUM(CASE WHEN result_status = 'S' THEN 1 ELSE 0 END), 0) AS successCount, "
            + "       COALESCE(SUM(CASE WHEN result_status = 'P' THEN 1 ELSE 0 END), 0) AS partialCount, "
            + "       COALESCE(SUM(CASE WHEN result_status = 'F' THEN 1 ELSE 0 END), 0) AS failCount, "
            + "       COALESCE(ROUND(AVG(duration_ms)), 0) AS avgDurationMs "
            + "FROM sync_log "
            + "<where>"
            + "  <if test='startDate != null and startDate != \"\"'>"
            + "    AND executed_at >= CONCAT(#{startDate}, ' 00:00:00')"
            + "  </if>"
            + "  <if test='endDate != null and endDate != \"\"'>"
            + "    AND CONCAT(#{endDate}, ' 23:59:59') >= executed_at"
            + "  </if>"
            + "</where>"
            + "</script>")
    SyncLogStatsResDto findSyncLogStats(SyncLogSearchReqDto search);


    // ==================================================================
    // admin-02 : 혜택 관리
    // ==================================================================

    /**
     * 혜택 목록.
     *
     * deadlineSoon 은 대시보드 '마감 임박' 카드에서, hasConflict 는 '중복수혜 규칙'
     * 카드에서 넘어올 때 쓰는 조건이라 활성 조건과 별개로 동작한다.
     *
     * hasConflict 는 그룹형(conflict_group_code)뿐 아니라 개별쌍 규칙에만 걸린 혜택도
     * 함께 잡는다. 둘 중 하나만 보면 '중복수혜 관리 대상'이 반쪽만 나온다.
     *
     * 목록에는 규칙 건수도 함께 내린다. 그룹 코드만 보여주면 개별쌍 규칙에만
     * 걸린 혜택이 '-' 로 표시되어, hasConflict 로 걸러낸 결과와 화면이 어긋난다.
     * 건수를 셋으로 나누는 이유는 규칙마다 엔진의 처리 방식이 다르기 때문이다.
     *   pair     : 양쪽 다 우리 정책이라 엔진이 후보에서 빼거나 감점한다
     *   external : 상대가 우리 DB에 없어(trigger NULL) 안내만 하고 점수는 건드리지 않는다
     *   review   : 아직 검수되지 않아 엔진이 무시한다. 관리자가 처리할 대상
     * 비활성(is_active='N') 규칙은 엔진이 쓰지 않으므로 어느 쪽에도 세지 않는다.
     *
     * 상태는 원본·관리자 지정·숨김을 모두 내린다. 관리 화면은 무엇이 덮였는지
     * 보여야 하므로 합친 값만 내리면 관리자가 자기가 지정한 것인지 알 수 없다.
     *
     * 정렬은 서버에서 한다. 목록이 2,700건인데 화면에는 20건만 내려가므로
     * 화면에서 정렬하면 '마감 임박순'이 전체 기준이 아니게 되어 틀린 결과가 된다.
     *
     * sort·order 는 화면에서 오는 값이라 SQL에 문자열로 이어붙이지 않는다.
     * 허용한 값만 <when> 으로 분기하고, 그 밖의 값은 기본 정렬로 떨어진다.
     */
    @Select("<script>"
            + "SELECT b.benefit_no, b.plcy_no, b.plcy_nm, b.category_code, b.sprvsn_inst_cd_nm, "
            + "       b.apply_end_date, b.aply_prd_se_cd, b.inq_cnt, "
            + "       b.conflict_group_code, b.frst_reg_dt, "

            // 상태 3종을 그대로 내리고 최종값을 따로 만든다
            + "       b.is_active, b.admin_is_active, b.api_deleted_yn, "
            + "       CASE WHEN b.api_deleted_yn = 'Y' THEN 'D' "
            + "            ELSE COALESCE(b.admin_is_active, b.is_active) END AS effective_status, "

            + "       CASE WHEN b.apply_end_date IS NULL THEN NULL "
            + "            ELSE DATEDIFF(b.apply_end_date, CURDATE()) END AS dday, "

            // 규칙 건수 3종. SELECT 절의 상관 서브쿼리는 LIMIT 으로 추려진
            // 행에만 계산되므로 2,700건 전체를 훑지 않는다.
            + "       (SELECT COUNT(*) FROM benefit_conflict_rule r "
            + "         WHERE r.confirm_status = '확정' AND r.is_active = 'Y' "
            + "           AND r.trigger_benefit_no IS NOT NULL "
            + "           AND (r.trigger_benefit_no = b.benefit_no "
            + "                OR r.target_benefit_no = b.benefit_no)) AS pair_rule_count, "
            + "       (SELECT COUNT(*) FROM benefit_conflict_rule r "
            + "         WHERE r.confirm_status = '확정' AND r.is_active = 'Y' "
            + "           AND r.trigger_benefit_no IS NULL "
            + "           AND r.target_benefit_no = b.benefit_no) AS external_rule_count, "
            + "       (SELECT COUNT(*) FROM benefit_conflict_rule r "
            + "         WHERE r.confirm_status = '검수필요' AND r.is_active = 'Y' "
            + "           AND (r.trigger_benefit_no = b.benefit_no "
            + "                OR r.target_benefit_no = b.benefit_no)) AS review_rule_count "

            + "FROM benefit b "
            + "<where>"
            + "  <if test='keyword != null and keyword != \"\"'>"
            + "    AND b.plcy_nm LIKE CONCAT('%', #{keyword}, '%')"
            + "  </if>"

            // 활성 필터는 최종 상태로 건다.
            // 관리자가 끈 정책을 '활성'으로 걸러 보여주면 화면과 필터가 어긋난다
            + "  <if test='isActive != null and isActive != \"\"'>"
            + "    AND b.api_deleted_yn = 'N' "
            + "    AND COALESCE(b.admin_is_active, b.is_active) = #{isActive}"
            + "  </if>"

            // 숨김 정책만 보기. 관리자가 무엇이 사라졌는지 확인할 때 쓴다
            + "  <if test='deletedOnly != null and deletedOnly'>"
            + "    AND b.api_deleted_yn = 'Y'"
            + "  </if>"

            // 관리자가 직접 지정한 정책만. 무엇을 손댔는지 되짚을 수 있어야 한다
            + "  <if test='adminManagedOnly != null and adminManagedOnly'>"
            + "    AND b.admin_is_active IS NOT NULL"
            + "  </if>"

            + "  <if test='categoryCode != null and categoryCode != \"\"'>"
            + "    AND b.category_code = #{categoryCode}"
            + "  </if>"
            + "  <if test='deadlineSoon != null and deadlineSoon'>"
            + "    AND b.apply_end_date IS NOT NULL "
            + "    AND b.apply_end_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)"
            + "  </if>"
            + "  <if test='hasConflict != null and hasConflict'>"
            + "    AND (b.conflict_group_code IS NOT NULL "
            + "         OR b.benefit_no IN ("
            + "             SELECT trigger_benefit_no FROM benefit_conflict_rule "
            + "              WHERE confirm_status = '확정' AND is_active = 'Y' "
            + "                AND trigger_benefit_no IS NOT NULL "
            + "             UNION "
            + "             SELECT target_benefit_no FROM benefit_conflict_rule "
            + "              WHERE confirm_status = '확정' AND is_active = 'Y'"
            + "         ))"
            + "  </if>"
            + "</where>"

            // 마감일이 NULL 인 혜택(상시 모집·기간 미정)은 어느 방향이든 맨 뒤로 보낸다.
            // 'apply_end_date IS NULL ASC' 는 값이 있는 행(0)을 먼저, NULL(1)을 뒤로 놓는다.
            // 어느 기준이든 benefit_no 를 마지막에 넣어 같은 값일 때 순서가 흔들리지 않게 한다.
            + "<choose>"
            + "  <when test='sort == \"plcyNm\"'>"
            + "    <choose>"
            + "      <when test='order == \"desc\"'>ORDER BY b.plcy_nm DESC, b.benefit_no DESC </when>"
            + "      <otherwise>ORDER BY b.plcy_nm ASC, b.benefit_no DESC </otherwise>"
            + "    </choose>"
            + "  </when>"
            + "  <when test='sort == \"sprvsnInstCdNm\"'>"
            + "    <choose>"
            + "      <when test='order == \"desc\"'>ORDER BY b.sprvsn_inst_cd_nm DESC, b.benefit_no DESC </when>"
            + "      <otherwise>ORDER BY b.sprvsn_inst_cd_nm ASC, b.benefit_no DESC </otherwise>"
            + "    </choose>"
            + "  </when>"
            + "  <when test='sort == \"deadline\"'>"
            + "    <choose>"
            + "      <when test='order == \"desc\"'>"
            + "        ORDER BY b.apply_end_date IS NULL ASC, b.apply_end_date DESC, b.benefit_no DESC </when>"
            + "      <otherwise>"
            + "        ORDER BY b.apply_end_date IS NULL ASC, b.apply_end_date ASC, b.benefit_no DESC </otherwise>"
            + "    </choose>"
            + "  </when>"
            + "  <when test='sort == \"inqCnt\"'>"
            + "    <choose>"
            + "      <when test='order == \"desc\"'>ORDER BY b.inq_cnt DESC, b.benefit_no DESC </when>"
            + "      <otherwise>ORDER BY b.inq_cnt ASC, b.benefit_no DESC </otherwise>"
            + "    </choose>"
            + "  </when>"
            + "  <otherwise>ORDER BY b.frst_reg_dt DESC, b.benefit_no DESC </otherwise>"
            + "</choose>"

            + "LIMIT #{offset}, #{size}"
            + "</script>")
    List<AdminBenefitListResDto> findBenefitList(AdminBenefitSearchReqDto search);

    /** 목록과 같은 조건의 전체 건수 */
    @Select("<script>"
            + "SELECT COUNT(*) FROM benefit "
            + "<where>"
            + "  <if test='keyword != null and keyword != \"\"'>"
            + "    AND plcy_nm LIKE CONCAT('%', #{keyword}, '%')"
            + "  </if>"
            + "  <if test='isActive != null and isActive != \"\"'>"
            + "    AND api_deleted_yn = 'N' "
            + "    AND COALESCE(admin_is_active, is_active) = #{isActive}"
            + "  </if>"
            + "  <if test='deletedOnly != null and deletedOnly'>"
            + "    AND api_deleted_yn = 'Y'"
            + "  </if>"
            + "  <if test='adminManagedOnly != null and adminManagedOnly'>"
            + "    AND admin_is_active IS NOT NULL"
            + "  </if>"
            + "  <if test='categoryCode != null and categoryCode != \"\"'>"
            + "    AND category_code = #{categoryCode}"
            + "  </if>"
            + "  <if test='deadlineSoon != null and deadlineSoon'>"
            + "    AND apply_end_date IS NOT NULL "
            + "    AND apply_end_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)"
            + "  </if>"
            + "  <if test='hasConflict != null and hasConflict'>"
            + "    AND (conflict_group_code IS NOT NULL "
            + "         OR benefit_no IN ("
            + "             SELECT trigger_benefit_no FROM benefit_conflict_rule "
            + "              WHERE confirm_status = '확정' AND is_active = 'Y' "
            + "                AND trigger_benefit_no IS NOT NULL "
            + "             UNION "
            + "             SELECT target_benefit_no FROM benefit_conflict_rule "
            + "              WHERE confirm_status = '확정' AND is_active = 'Y'"
            + "         ))"
            + "  </if>"
            + "</where>"
            + "</script>")
    int countBenefitList(AdminBenefitSearchReqDto search);

    /**
     * 혜택 상세.
     * region_count는 지역 매핑 개수로, 200을 넘으면 전국 코드가 부여된 혜택이다.
     * 주관기관이 특정 지자체인데 이 값이 크면 원천 데이터 오류를 의심할 수 있다.
     *
     * 신청 URL은 원본·참고·관리자 지정을 모두 내린다.
     * 관리 화면에서는 무엇이 덮였는지 보여야 하므로 COALESCE로 합치지 않는다.
     * 활성 상태도 같은 이유로 세 값을 모두 내린다.
     */
    @Select("SELECT b.benefit_no, b.plcy_no, b.plcy_nm, b.category_code, b.sprvsn_inst_cd_nm, " +
            "       b.target_desc, b.plcy_sprt_cn, b.plcy_aply_mthd_cn, b.sbmsn_dcmnt_cn, " +
            "       b.plcy_expln_cn, b.aply_url_addr, b.custom_apply_url, b.ref_url_addr1, " +
            "       b.apply_start_date, b.apply_end_date, b.aply_ymd, b.aply_prd_se_cd, " +
            "       b.sprt_trgt_min_age, b.sprt_trgt_max_age, b.earn_cnd_se_cd, " +
            "       b.earn_min_amt, b.earn_max_amt, b.earn_etc_cn, " +
            "       b.mrg_stts_cd, b.conflict_group_code, b.inq_cnt, " +
            "       b.is_active, b.admin_is_active, b.api_deleted_yn, b.api_deleted_dt, " +
            "       CASE WHEN b.api_deleted_yn = 'Y' THEN 'D' " +
            "            ELSE COALESCE(b.admin_is_active, b.is_active) END AS effective_status, " +
            "       b.frst_reg_dt, b.last_mdfcn_dt, " +
            "       (SELECT COUNT(*) FROM benefit_region br " +
            "         WHERE br.benefit_no = b.benefit_no) AS region_count " +
            "FROM benefit b " +
            "WHERE b.benefit_no = #{benefitNo}")
    AdminBenefitDetailResDto findBenefitDetail(@Param("benefitNo") int benefitNo);

    /**
     * 관리자 지정 활성 상태를 저장한다.
     *
     * 지정값을 별도 컬럼에 두고 원본 is_active 도 함께 맞춘다.
     * 원본까지 바꾸는 이유는 사용자 화면과 엔진의 조회 쿼리가 여러 곳에 흩어져
     * is_active 를 직접 보고 있어, 각각에 COALESCE 를 넣는 대신
     * 값 자체를 맞춰두는 편이 안전하기 때문이다.
     *
     * 동기화가 원본을 덮으면 trg_benefit_keep_admin_active 트리거가
     * 다시 지정값으로 되돌린다. 관리자가 누른 행위는 그렇게 보존된다.
     *
     * null 을 넣으면 지정을 해제하고 그때부터 API 원본을 따른다.
     * last_mdfcn_dt 는 건드리지 않는다. 그 값은 원천 데이터의 수정 시각이라
     * 관리자 조작으로 바꾸면 동기화 판단 기준이 흔들린다.
     */
    @Update("UPDATE benefit " +
            "SET admin_is_active = #{adminIsActive,jdbcType=CHAR}, " +
            "    is_active = COALESCE(#{adminIsActive,jdbcType=CHAR}, is_active) " +
            "WHERE benefit_no = #{benefitNo}")
    int updateAdminActive(@Param("benefitNo") int benefitNo,
                          @Param("adminIsActive") String adminIsActive);

    /**
     * 관리자 지정 신청 URL 을 저장한다.
     * NULLIF(TRIM(...), '') 로 빈 문자열을 null 과 같게 만든다.
     * 원본 aply_url_addr 에 빈 문자열인 행이 다수라 같은 함정을 만들지 않기 위해서다.
     */
    @Update("UPDATE benefit " +
            "SET custom_apply_url = NULLIF(TRIM(#{customApplyUrl,jdbcType=VARCHAR}), '') " +
            "WHERE benefit_no = #{benefitNo}")
    int updateCustomApplyUrl(@Param("benefitNo") int benefitNo,
                             @Param("customApplyUrl") String customApplyUrl);

    /** 동기화 처리 내역 일괄 기록 */
    @Insert("<script>"
            + "INSERT INTO sync_log_detail (log_no, benefit_no, action_type, changed_summary) VALUES "
            + "<foreach collection='list' item='item' separator=','>"
            + "  (#{item.logNo}, #{item.benefitNo}, #{item.actionType}, #{item.changedSummary})"
            + "</foreach>"
            + "</script>")
    int insertSyncLogDetails(@Param("list") List<SyncLogDetailVO> details);

    /**
     * 동기화 처리 내역 조회.
     * 신규(I)를 먼저 보여주고 그다음 갱신(U), 마지막에 삭제(D)를 둔다.
     * 관리자가 가장 먼저 확인하고 싶은 것이 새로 들어온 정책이기 때문이다.
     */
    @Select("SELECT d.detail_no, d.log_no, d.benefit_no, d.action_type, d.changed_summary, " +
            "       b.plcy_nm, b.category_code, b.sprvsn_inst_cd_nm, b.inq_cnt " +
            "FROM sync_log_detail d " +
            "JOIN benefit b ON b.benefit_no = d.benefit_no " +
            "WHERE d.log_no = #{logNo} " +
            "ORDER BY FIELD(d.action_type, 'I', 'U', 'D'), d.detail_no ASC")
    List<SyncLogDetailResDto> findSyncLogDetails(@Param("logNo") int logNo);
}