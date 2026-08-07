package org.scoula.admin.controller;

import lombok.RequiredArgsConstructor;
import org.scoula.admin.dto.AdminBenefitDetailResDto;
import org.scoula.admin.dto.AdminBenefitPageResDto;
import org.scoula.admin.dto.AdminBenefitSearchReqDto;
import org.scoula.admin.dto.DashboardResDto;
import org.scoula.admin.dto.SyncLogDetailResDto;
import org.scoula.admin.dto.SyncLogPageResDto;
import org.scoula.admin.dto.SyncLogSearchReqDto;
import org.scoula.admin.dto.SyncResultResDto;
import org.scoula.admin.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.scoula.security.account.domain.CustomUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

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
     * admin-03: 특정 동기화가 처리한 혜택 목록
     * 로그에는 건수만 남아 있어 무엇이 갱신됐는지 알 수 없으므로 건별 내역을 제공한다.
     */
    @GetMapping("/synclog/{logNo}/details")
    public ResponseEntity<List<SyncLogDetailResDto>> getSyncLogDetails(@PathVariable int logNo) {
        return ResponseEntity.ok(adminService.getSyncLogDetails(logNo));
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
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        AdminBenefitSearchReqDto search = new AdminBenefitSearchReqDto();
        search.setKeyword(keyword);
        search.setIsActive(isActive);
        search.setCategoryCode(categoryCode);
        search.setDeadlineSoon(deadlineSoon);
        search.setHasConflict(hasConflict);
        search.setSort(sort);
        search.setOrder(order);
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
     * admin-02: 혜택 활성 상태 변경
     * 물리 삭제는 FK 제약으로 불가능해 상태 변경 방식만 제공한다.
     */
    @PatchMapping("/benefits/{benefitNo}/active")
    public ResponseEntity<AdminBenefitDetailResDto> changeBenefitActive(
            @PathVariable int benefitNo,
            @RequestParam String isActive) {

        return ResponseEntity.ok(adminService.changeBenefitActive(benefitNo, isActive));
    }

    /**
     * admin-02: 관리자 지정 신청 URL 저장·해제
     *
     * customApplyUrl을 비우거나 보내지 않으면 지정을 해제하고 원본 URL로 되돌린다.
     * 원본은 지우지 않으므로 되돌리기가 항상 가능하다.
     */
    @PatchMapping("/benefits/{benefitNo}/apply-url")
    public ResponseEntity<AdminBenefitDetailResDto> changeCustomApplyUrl(
            @PathVariable int benefitNo,
            @RequestParam(required = false) String customApplyUrl) {

        return ResponseEntity.ok(adminService.changeCustomApplyUrl(benefitNo, customApplyUrl));
    }

    /**
     * admin-01: 관리자 수동 동기화 실행 (페이지 범위)
     * 화면에서는 제거했고 개발 확인용으로만 남겨둔다.
     *
     * 실행자는 파라미터로 받지 않는다. SecurityConfig가 ADMIN 권한을 이미 확인했고,
     * 토큰에서 꺼내야 남의 이름으로 기록을 남길 수 없다.
     */
    @PostMapping("/sync")
    public ResponseEntity<SyncResultResDto> executeSync(
            @AuthenticationPrincipal CustomUser user,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "100") Integer pageSize) {

        SyncResultResDto result =
                adminService.executeSync(pageNum, pageSize, user.getMember().getMemberNo());
        return ResponseEntity.ok(result);
    }
    /**
     * admin-01: 관리자 기간별 동기화 실행
     * 기준은 정책의 최초등록일(frst_reg_dt)이며 신청 기간이 아니다.
     * 날짜 형식은 yyyy-MM-dd 또는 yyyyMMdd.
     */
    @PostMapping("/sync/period")
    public ResponseEntity<SyncResultResDto> executeSyncByPeriod(
            @AuthenticationPrincipal CustomUser user,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        SyncResultResDto result =
                adminService.executeSyncByPeriod(startDate, endDate, user.getMember().getMemberNo());
        return ResponseEntity.ok(result);
    }
}