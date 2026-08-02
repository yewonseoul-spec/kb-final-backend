package org.scoula.admin.service;

import org.scoula.admin.dto.DashboardResDto;
import org.scoula.admin.dto.AdminBenefitDetailResDto;
import org.scoula.admin.dto.AdminBenefitPageResDto;
import org.scoula.admin.dto.AdminBenefitSearchReqDto;
import org.scoula.admin.dto.SyncLogPageResDto;
import org.scoula.admin.dto.SyncLogSearchReqDto;
import org.scoula.admin.dto.SyncResultResDto;

public interface AdminService {

    // admin-01: 관리자 대시보드 운영 현황
    DashboardResDto getDashboard();

    // admin-03: 동기화 로그 목록 조회
    SyncLogPageResDto getSyncLogs(SyncLogSearchReqDto search);

    // admin-02: 혜택 목록 조회
    AdminBenefitPageResDto getBenefits(AdminBenefitSearchReqDto search);

    // admin-02: 혜택 상세 조회
    AdminBenefitDetailResDto getBenefitDetail(int benefitNo);

    // admin-02: 혜택 노출 상태 변경
    AdminBenefitDetailResDto changeBenefitActive(int benefitNo, String isActive);

    // admin-01: 관리자 수동 동기화 실행 (페이지 범위)
    SyncResultResDto executeSync(Integer pageNum, Integer pageSize, Integer memberNo);

    // admin-01: 관리자 기간별 동기화 실행 (정책 최초등록일 기준)
    SyncResultResDto executeSyncByPeriod(String startDate, String endDate, Integer memberNo);
}