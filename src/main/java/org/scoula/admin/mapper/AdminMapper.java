package org.scoula.admin.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.scoula.admin.domain.SyncLogVO;

public interface AdminMapper {

    // admin-01: 동기화 전후 비교용 정책 총 건수
    @Select("SELECT COUNT(*) FROM benefit")
    int countBenefits();

    // admin-01: 동기화 실행 이력 기록 (admin-02 이력 조회의 재료)
    @Insert("INSERT INTO sync_log (" +
            "executed_at, exec_type, result_status, total_cnt, " +
            "insert_cnt, update_cnt, skip_cnt, error_msg, duration_ms, member_no" +
            ") VALUES (" +
            "NOW(), #{execType}, #{resultStatus}, #{totalCnt}, " +
            "#{insertCnt}, #{updateCnt}, #{skipCnt}, #{errorMsg}, #{durationMs}, #{memberNo}" +
            ")")
    int insertSyncLog(SyncLogVO syncLog);
}
