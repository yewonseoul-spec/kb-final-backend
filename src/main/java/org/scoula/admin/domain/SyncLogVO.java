package org.scoula.admin.domain;

import lombok.Data;
import java.util.Date;

@Data
public class SyncLogVO {
    private Integer logNo;
    private Date executedAt;
    private String execType;       // A=자동, M=수동
    private String resultStatus;   // S=성공, P=부분성공, F=실패
    private Integer totalCnt;
    private Integer insertCnt;
    private Integer updateCnt;
    private Integer skipCnt;
    private String errorMsg;
    private Integer durationMs;
    private Integer memberNo;      // 실행한 관리자 (자동 동기화면 null)
}