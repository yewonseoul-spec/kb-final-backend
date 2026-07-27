package org.scoula.admin.service;

import org.scoula.admin.dto.SyncResultResDto;

public interface AdminService {

    // admin-01: 관리자 수동 동기화 실행
    SyncResultResDto executeSync(Integer pageNum, Integer pageSize, Integer memberNo);
}