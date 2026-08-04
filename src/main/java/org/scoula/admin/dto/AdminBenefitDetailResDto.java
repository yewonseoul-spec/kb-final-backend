package org.scoula.admin.dto;

import lombok.Data;

import java.util.Date;

// admin-02: 혜택 상세. 목록에 없는 본문 항목까지 포함한다.
@Data
public class AdminBenefitDetailResDto {
    private Integer benefitNo;
    private String plcyNo;
    private String plcyNm;
    private String categoryCode;
    private String sprvsnInstCdNm;

    private String targetDesc;       // 지원 대상
    private String plcySprtCn;       // 지원 내용
    private String plcyAplyMthdCn;   // 신청 방법
    private String sbmsnDcmntCn;     // 제출 서류
    private String plcyExplnCn;      // 혜택 설명
    private String aplyUrlAddr;      // 신청 URL

    private Date applyStartDate;
    private Date applyEndDate;
    private String aplyYmd;
    private String aplyPrdSeCd;

    private Integer sprtTrgtMinAge;
    private Integer sprtTrgtMaxAge;
    private String earnCndSeCd;
    private Integer earnMinAmt;
    private Integer earnMaxAmt;
    private String earnEtcCn;        // 기타 소득조건 원문
    private String mrgSttsCd;

    private String conflictGroupCode;
    private Integer inqCnt;
    private String isActive;
    private Date frstRegDt;
    private Date lastMdfcnDt;

    private Integer regionCount;     // 매핑 건수. 전국 코드 오부여 판단에 쓴다
}