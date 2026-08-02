package org.scoula.admin.dto;

import lombok.Data;

import java.util.Date;

// admin-02: 혜택 목록 한 행 (MyBatis resultType이라 setter 필요)
@Data
public class AdminBenefitListResDto {
    private Integer benefitNo;
    private String plcyNo;
    private String plcyNm;
    private String categoryCode;
    private String sprvsnInstCdNm;   // 주관기관. 동명 혜택 구분에 필요
    private Date applyEndDate;
    private Integer dday;            // 마감까지 남은 일수. 상시모집이면 null
    private String aplyPrdSeCd;
    private String isActive;
    private Integer inqCnt;
    private String conflictGroupCode;
    private Date frstRegDt;
}