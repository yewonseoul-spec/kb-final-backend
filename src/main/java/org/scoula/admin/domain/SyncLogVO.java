package org.scoula.admin.domain;

import lombok.Data;

import java.util.Date;

@Data
public class SyncLogVO {
    private Integer logNo;
    private Date executedAt;       // 실행 시각 (INSERT는 NOW(), 조회 시 화면 표시용)
    private String execType;       // A=자동, M=수동
    private Date syncStartDate;    // 동기화 대상 등록일 시작 (자동 실행이면 null)
    private Date syncEndDate;      // 동기화 대상 등록일 종료
    private String resultStatus;   // S=성공, P=부분성공, F=실패
    private Integer totalCnt;
    private Integer insertCnt;
    private Integer updateCnt;
    private Integer skipCnt;
    private String errorMsg;
    private Integer durationMs;
    private Integer memberNo;      // 실행한 관리자 (자동 동기화면 null)
}