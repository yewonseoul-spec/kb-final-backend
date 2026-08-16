package org.scoula.admin.controller;

import lombok.RequiredArgsConstructor;
import org.scoula.admin.dto.RecommendKeywordAdminDTO;
import org.scoula.admin.dto.RecommendKeywordCreateDTO;
import org.scoula.admin.dto.RecommendKeywordStatusDTO;
import org.scoula.admin.service.AdminService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/recommend-keywords")
@RequiredArgsConstructor
public class AdminRecommendKeywordController {

    private final AdminService adminService;

    // 추천검색어 전체 조회
    @GetMapping
    public List<RecommendKeywordAdminDTO> getKeywords() {
        return adminService.getRecommendKeywords();
    }

    // 추천검색어 추가
    @PostMapping
    public void create(
            @RequestBody RecommendKeywordCreateDTO dto
    ) {
        adminService.createRecommendKeyword(
                dto.getKeywordName()
        );
    }

    // 활성 / 비활성 변경
    @PatchMapping("/{keywordCode}/status")
    public void updateStatus(
            @PathVariable Integer keywordCode,
            @RequestBody RecommendKeywordStatusDTO dto
    ) {
        adminService.changeRecommendKeywordStatus(
                keywordCode,
                dto.getIsActive()
        );
    }

    // 삭제
    @DeleteMapping("/{keywordCode}")
    public void delete(
            @PathVariable Integer keywordCode
    ) {
        adminService.deleteRecommendKeyword(
                keywordCode
        );
    }
}