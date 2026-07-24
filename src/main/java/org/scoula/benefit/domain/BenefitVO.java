package org.scoula.benefit.domain;

import lombok.Data;

@Data
public class BenefitVO {
    private Integer benefitNo;

    private String plcyNo;
    private String plcyNm;
    private String categoryCode;
    private String sprvsnInstCdNm;

    private String targetDesc;
    private String plcySprtCn;
    private Integer supportAmount;

    private String plcyAplyMthdCn;
    private String sbmsnDcmntCn;

    private String applyStartDate;
    private String applyEndDate;
    private String aplyYmd;
    private String aplyUrlAddr;

    private Integer sprtTrgtMinAge;
    private Integer sprtTrgtMaxAge;

    private String earnCndSeCd;
    private Integer earnMinAmt;
    private Integer earnMaxAmt;
    private String earnEtcCn;

    private String mrgSttsCd;
    private String plcyMajorCd;
    private String schoolCd;
    private String jobCd;

    private String conflictGroupCode;
    private Integer inqCnt;

    private String isActive;

    private String frstRegDt;
    private String lastMdfcnDt;

    private String plcyExplnCn;
}