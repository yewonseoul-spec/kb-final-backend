package org.scoula.admin.controller;

import lombok.RequiredArgsConstructor;
import org.scoula.admin.dto.DashboardResDto;
import org.scoula.admin.dto.SyncLogPageResDto;
import org.scoula.admin.dto.SyncLogSearchReqDto;
import org.scoula.admin.dto.SyncResultResDto;
import org.scoula.admin.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.scoula.admin.dto.AdminBenefitDetailResDto;
import org.scoula.admin.dto.AdminBenefitPageResDto;
import org.scoula.admin.dto.AdminBenefitSearchReqDto;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /**
     * admin-01: 관리자 대시보드 운영 현황
     * 통계 카드 5종 + 최근 동기화 이력 5건 + 마감 임박 정책 5건
     */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResDto> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboard());
    }

    /**
     * admin-03: 동기화 로그 목록 조회
     * 모든 조건은 선택이며, 아무것도 안 주면 전체를 최신순으로 반환한다.
     *
     *   startDate/endDate : yyyy-MM-dd (실행 시각 기준)
     *   resultStatus      : S / P / F
     *   execType          : A / M
     */
    @GetMapping("/synclog")
    public ResponseEntity<SyncLogPageResDto> getSyncLogs(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String resultStatus,
            @RequestParam(required = false) String execType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        SyncLogSearchReqDto search = new SyncLogSearchReqDto();
        search.setStartDate(startDate);
        search.setEndDate(endDate);
        search.setResultStatus(resultStatus);
        search.setExecType(execType);
        search.setPage(page);
        search.setSize(size);

        return ResponseEntity.ok(adminService.getSyncLogs(search));
    }

    /**
     * admin-02: 혜택 목록 조회
     * 모든 조건은 선택이며, 아무것도 안 주면 전체를 최신 등록순으로 반환한다.
     */
    @GetMapping("/benefits")
    public ResponseEntity<AdminBenefitPageResDto> getBenefits(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String isActive,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) Boolean deadlineSoon,
            @RequestParam(required = false) Boolean hasConflict,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        AdminBenefitSearchReqDto search = new AdminBenefitSearchReqDto();
        search.setKeyword(keyword);
        search.setIsActive(isActive);
        search.setCategoryCode(categoryCode);
        search.setDeadlineSoon(deadlineSoon);
        search.setHasConflict(hasConflict);
        search.setPage(page);
        search.setSize(size);

        return ResponseEntity.ok(adminService.getBenefits(search));
    }

    /** admin-02: 혜택 상세 조회 */
    @GetMapping("/benefits/{benefitNo}")
    public ResponseEntity<AdminBenefitDetailResDto> getBenefitDetail(@PathVariable int benefitNo) {
        return ResponseEntity.ok(adminService.getBenefitDetail(benefitNo));
    }

    /**
     * admin-02: 혜택 노출 상태 변경
     * 물리 삭제는 FK 제약으로 불가능해 상태 변경 방식만 제공한다.
     */
    @PatchMapping("/benefits/{benefitNo}/active")
    public ResponseEntity<AdminBenefitDetailResDto> changeBenefitActive(
            @PathVariable int benefitNo,
            @RequestParam String isActive) {

        return ResponseEntity.ok(adminService.changeBenefitActive(benefitNo, isActive));
    }

    /**
     * admin-01: 관리자 수동 동기화 실행 (페이지 범위)
     * 온통청년 API에 기간 조회 파라미터가 연결돼 있지 않아
     * 페이지 범위로 동기화 대상을 조절한다.
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