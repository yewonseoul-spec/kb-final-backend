package org.scoula.admin.dto;

import lombok.Data;

// admin-03: 동기화 로그 검색 조건. 모든 조건은 선택이며 null이면 무시된다.
@Data
public class SyncLogSearchReqDto {
    private String startDate;      // yyyy-MM-dd (실행 시각 기준)
    private String endDate;        // yyyy-MM-dd
    private String resultStatus;   // S / P / F
    private String execType;       // A / M

    private Integer page;          // 1부터
    private Integer size;
    private Integer offset;        // 서비스에서 계산해 채운다
}