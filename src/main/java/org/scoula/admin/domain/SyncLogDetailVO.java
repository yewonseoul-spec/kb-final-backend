package org.scoula.admin.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

// sync_log_detail 한 행. 동기화가 어떤 혜택을 어떻게 처리했는지 남긴다.
@Data
@AllArgsConstructor
public class SyncLogDetailVO {
    private Integer logNo;
    private Integer benefitNo;
    private String actionType;       // I=신규 / U=갱신
    private String changedSummary;   // 갱신 시 바뀐 내용. 신규거나 변경 없으면 null
}