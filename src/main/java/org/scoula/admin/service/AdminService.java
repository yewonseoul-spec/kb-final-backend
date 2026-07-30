package org.scoula.admin.service;

import org.scoula.admin.dto.SyncResultResDto;

public interface AdminService {

    // admin-01: 관리자 수동 동기화 실행 (페이지 범위)
    SyncResultResDto executeSync(Integer pageNum, Integer pageSize, Integer memberNo);

    // admin-01: 관리자 기간별 동기화 실행 (정책 최초등록일 기준)
    SyncResultResDto executeSyncByPeriod(String startDate, String endDate, Integer memberNo);
}