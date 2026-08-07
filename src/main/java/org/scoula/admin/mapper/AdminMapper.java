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

public interface AdminMapper {

    // ==================================================================
    // admin-01 : 대시보드
    // ==================================================================

    // 동기화 전후 비교용 정책 총 건수 (대시보드 '전체 정책' 카드와 공용)
    @Select("SELECT COUNT(*) FROM benefit")
    int countBenefits();

    // 추천 가능 정책 수. 마감·미도래를 뺀 실제 추천 대상
    @Select("SELECT COUNT(*) FROM benefit WHERE is_active = 'Y'")
    int countActiveBenefits();

    // 30일 이내 마감 정책 수. 상시모집(apply_end_date NULL)은 제외
    @Select("SELECT COUNT(*) FROM benefit " +
            "WHERE is_active = 'Y' " +
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
            "WHERE is_active = 'Y' " +
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
     * 카드에서 넘어올 때 쓰는 조건이라 is_active 조건과 별개로 동작한다.
     *
     * hasConflict 는 그룹형(conflict_group_code)뿐 아니라 개별쌍 규칙에만 걸린 혜택도
     * 함께 잡는다. 둘 중 하나만 보면 '중복수혜 관리 대상'이 반쪽만 나온다.
     *
     * 정렬은 서버에서 한다. 목록이 2,700건인데 화면에는 20건만 내려가므로
     * 화면에서 정렬하면 '마감 임박순'이 전체 기준이 아니게 되어 틀린 결과가 된다.
     *
     * sort·order 는 화면에서 오는 값이라 SQL에 문자열로 이어붙이지 않는다.
     * 허용한 값만 <when> 으로 분기하고, 그 밖의 값은 기본 정렬로 떨어진다.
     */
    @Select("<script>"
            + "SELECT benefit_no, plcy_no, plcy_nm, category_code, sprvsn_inst_cd_nm, "
            + "       apply_end_date, aply_prd_se_cd, is_active, inq_cnt, "
            + "       conflict_group_code, frst_reg_dt, "
            + "       CASE WHEN apply_end_date IS NULL THEN NULL "
            + "            ELSE DATEDIFF(apply_end_date, CURDATE()) END AS dday "
            + "FROM benefit "
            + "<where>"
            + "  <if test='keyword != null and keyword != \"\"'>"
            + "    AND plcy_nm LIKE CONCAT('%', #{keyword}, '%')"
            + "  </if>"
            + "  <if test='isActive != null and isActive != \"\"'>"
            + "    AND is_active = #{isActive}"
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

            // 마감일이 NULL 인 혜택(상시 모집·기간 미정)은 어느 방향이든 맨 뒤로 보낸다.
            // 'apply_end_date IS NULL ASC' 는 값이 있는 행(0)을 먼저, NULL(1)을 뒤로 놓는다.
            // 어느 기준이든 benefit_no 를 마지막에 넣어 같은 값일 때 순서가 흔들리지 않게 한다.
            + "<choose>"
            + "  <when test='sort == \"plcyNm\"'>"
            + "    <choose>"
            + "      <when test='order == \"desc\"'>ORDER BY plcy_nm DESC, benefit_no DESC </when>"
            + "      <otherwise>ORDER BY plcy_nm ASC, benefit_no DESC </otherwise>"
            + "    </choose>"
            + "  </when>"
            + "  <when test='sort == \"sprvsnInstCdNm\"'>"
            + "    <choose>"
            + "      <when test='order == \"desc\"'>ORDER BY sprvsn_inst_cd_nm DESC, benefit_no DESC </when>"
            + "      <otherwise>ORDER BY sprvsn_inst_cd_nm ASC, benefit_no DESC </otherwise>"
            + "    </choose>"
            + "  </when>"
            + "  <when test='sort == \"deadline\"'>"
            + "    <choose>"
            + "      <when test='order == \"desc\"'>"
            + "        ORDER BY apply_end_date IS NULL ASC, apply_end_date DESC, benefit_no DESC </when>"
            + "      <otherwise>"
            + "        ORDER BY apply_end_date IS NULL ASC, apply_end_date ASC, benefit_no DESC </otherwise>"
            + "    </choose>"
            + "  </when>"
            + "  <when test='sort == \"inqCnt\"'>"
            + "    <choose>"
            + "      <when test='order == \"desc\"'>ORDER BY inq_cnt DESC, benefit_no DESC </when>"
            + "      <otherwise>ORDER BY inq_cnt ASC, benefit_no DESC </otherwise>"
            + "    </choose>"
            + "  </when>"
            + "  <otherwise>ORDER BY frst_reg_dt DESC, benefit_no DESC </otherwise>"
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
            + "    AND is_active = #{isActive}"
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
     */
    @Select("SELECT b.benefit_no, b.plcy_no, b.plcy_nm, b.category_code, b.sprvsn_inst_cd_nm, " +
            "       b.target_desc, b.plcy_sprt_cn, b.plcy_aply_mthd_cn, b.sbmsn_dcmnt_cn, " +
            "       b.plcy_expln_cn, b.aply_url_addr, " +
            "       b.apply_start_date, b.apply_end_date, b.aply_ymd, b.aply_prd_se_cd, " +
            "       b.sprt_trgt_min_age, b.sprt_trgt_max_age, " +
            "       b.earn_cnd_se_cd, b.earn_min_amt, b.earn_max_amt, b.earn_etc_cn, " +
            "       b.mrg_stts_cd, b.conflict_group_code, b.inq_cnt, b.is_active, " +
            "       b.frst_reg_dt, b.last_mdfcn_dt, " +
            "       (SELECT COUNT(*) FROM benefit_region br " +
            "         WHERE br.benefit_no = b.benefit_no) AS region_count " +
            "FROM benefit b WHERE b.benefit_no = #{benefitNo}")
    AdminBenefitDetailResDto findBenefitDetail(@Param("benefitNo") int benefitNo);

    /**
     * 혜택 활성 상태 변경.
     * 물리 삭제는 benefit_region 등 7개 테이블이 FK로 참조하고 있어 불가능하다.
     */
    @Update("UPDATE benefit SET is_active = #{isActive}, last_mdfcn_dt = NOW() " +
            "WHERE benefit_no = #{benefitNo}")
    int updateBenefitActive(@Param("benefitNo") int benefitNo,
                            @Param("isActive") String isActive);

    // ==================================================================
    // admin-03 : 동기화 갱신 내역
    // ==================================================================

    /** 처리 내역 일괄 저장. 수백 건까지 나올 수 있어 한 번에 넣는다 */
    @Insert("<script>"
            + "INSERT INTO sync_log_detail (log_no, benefit_no, action_type, changed_summary) VALUES "
            + "<foreach collection='list' item='d' separator=','>"
            + "  (#{d.logNo}, #{d.benefitNo}, #{d.actionType}, #{d.changedSummary})"
            + "</foreach>"
            + "</script>")
    int insertSyncLogDetails(List<SyncLogDetailVO> details);

    /**
     * 특정 동기화가 처리한 혜택 목록.
     * 신규(I)를 먼저, 그 안에서는 실제로 값이 바뀐 건을 먼저 보여준다.
     * 갱신 대상이어도 내용이 그대로인 경우가 많아 변경분이 뒤로 밀리면 확인하기 어렵다.
     */
    @Select("SELECT d.benefit_no, d.action_type, d.changed_summary, " +
            "       b.plcy_nm, b.category_code, b.sprvsn_inst_cd_nm, " +
            "       b.is_active, b.inq_cnt, b.apply_end_date " +
            "FROM sync_log_detail d " +
            "JOIN benefit b ON b.benefit_no = d.benefit_no " +
            "WHERE d.log_no = #{logNo} " +
            "ORDER BY d.action_type ASC, " +
            "         CASE WHEN d.changed_summary IS NULL THEN 1 ELSE 0 END, " +
            "         d.detail_no ASC")
    List<SyncLogDetailResDto> findSyncLogDetails(@Param("logNo") int logNo);
}