package org.scoula.admin.dto;

import lombok.Data;

import java.util.Date;

// admin-03: 동기화 갱신 내역 한 행 (MyBatis resultType이라 setter 필요)
@Data
public class SyncLogDetailResDto {
    private Integer benefitNo;
    private String plcyNm;
    private String categoryCode;
    private String sprvsnInstCdNm;
    private String actionType;       // I=신규 / U=갱신
    private String changedSummary;   // 갱신 시 바뀐 내용. 신규거나 변경 없으면 null
    private String isActive;
    private Integer inqCnt;
    private Date applyEndDate;
}