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
}
