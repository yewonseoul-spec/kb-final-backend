package org.scoula.admin.dto;

import lombok.Data;

// admin-03: 로그 화면 상단 통계 카드 (MyBatis resultType이라 setter 필요)
@Data
public class SyncLogStatsResDto {
    private int totalCount;     // 전체 실행
    private int successCount;   // 성공
    private int partialCount;   // 부분 성공
    private int failCount;      // 실패
    private int avgDurationMs;  // 평균 소요시간
}
