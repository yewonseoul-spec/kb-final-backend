package org.scoula.admin.dto;

import lombok.Builder;
import lombok.Data;
import org.scoula.admin.domain.SyncLogVO;

import java.util.List;

// admin-01: 관리자 대시보드 운영 현황
@Data
@Builder
public class DashboardResDto {

    private int totalBenefits;      // 전체 정책 수
    private int activeBenefits;     // 추천 가능 정책 (is_active='Y')
    private int deadlineSoonCount;  // 30일 이내 마감
    private int conflictRuleCount;  // 확정·활성 중복수혜 규칙
    private int memberCount;        // 활동 회원

    private List<SyncLogVO> recentSyncLogs;              // 최근 동기화 5건
    private List<DeadlineBenefitResDto> deadlineBenefits; // 마감 임박 5건
}