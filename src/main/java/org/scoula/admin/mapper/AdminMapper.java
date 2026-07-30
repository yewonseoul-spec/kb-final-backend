package org.scoula.admin.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.scoula.admin.domain.SyncLogVO;
import org.scoula.admin.dto.DeadlineBenefitResDto;
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

    // 추천 가능 정책 수. 마감·미도래를 뺀 실제 노출 대상
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
    @Select("SELECT log_no, executed_at, exec_type, result_status, " +
            "       total_cnt, insert_cnt, update_cnt, skip_cnt, " +
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
            "executed_at, exec_type, result_status, total_cnt, " +
            "insert_cnt, update_cnt, skip_cnt, error_msg, duration_ms, member_no" +
            ") VALUES (" +
            "NOW(), #{execType}, #{resultStatus}, #{totalCnt}, " +
            "#{insertCnt}, #{updateCnt}, #{skipCnt}, #{errorMsg}, #{durationMs}, #{memberNo}" +
            ")")
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
            + "SELECT log_no, executed_at, exec_type, result_status, "
            + "       total_cnt, insert_cnt, update_cnt, skip_cnt, "
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
}