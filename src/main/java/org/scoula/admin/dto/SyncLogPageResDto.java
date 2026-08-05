package org.scoula.admin.dto;

import lombok.Builder;
import lombok.Data;
import org.scoula.admin.domain.SyncLogVO;

import java.util.List;

// admin-03: 동기화 로그 목록 응답
@Data
@Builder
public class SyncLogPageResDto {
    private int page;
    private int size;
    private int totalCount;
    private int totalPages;

    private SyncLogStatsResDto stats;   // 상단 카드 (기간만 반영, 상태 필터 미적용)
    private List<SyncLogVO> logs;
}