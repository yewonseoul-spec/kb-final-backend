package org.scoula.admin.controller;

import lombok.RequiredArgsConstructor;
import org.scoula.admin.dto.SyncResultResDto;
import org.scoula.admin.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /**
     * admin-01: 관리자 수동 동기화 실행
     * 온통청년 API에 기간 조회 파라미터가 연결돼 있지 않아
     * 현재는 페이지 범위로 동기화 대상을 조절한다.
     */
    @PostMapping("/sync")
    public ResponseEntity<SyncResultResDto> executeSync(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "100") Integer pageSize,
            @RequestParam(required = false) Integer memberNo) {

        SyncResultResDto result = adminService.executeSync(pageNum, pageSize, memberNo);
        return ResponseEntity.ok(result);
    }
    /**
     * admin-01: 관리자 기간별 동기화 실행
     * 기준은 정책의 최초등록일(frst_reg_dt)이며 신청 기간이 아니다.
     * 날짜 형식은 yyyy-MM-dd 또는 yyyyMMdd.
     */
    @PostMapping("/sync/period")
    public ResponseEntity<SyncResultResDto> executeSyncByPeriod(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) Integer memberNo) {

        SyncResultResDto result = adminService.executeSyncByPeriod(startDate, endDate, memberNo);
        return ResponseEntity.ok(result);
    }
}
