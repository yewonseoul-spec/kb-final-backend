package org.scoula.benefit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class YouthPolicyApiItemDTO {
    private String plcyNo;
    private String plcyNm;
    private String plcyKywdNm;
    private String plcyExplnCn;

    private String lclsfNm;
    private String mclsfNm;

    private String sprvsnInstCdNm;
    private String plcySprtCn;
    private String plcyAplyMthdCn;
    private String sbmsnDcmntCn;

    private String aplyYmd;
    private String aplyUrlAddr;

    private String sprtTrgtMinAge;
    private String sprtTrgtMaxAge;

    private String earnCndSeCd;
    private String earnMinAmt;
    private String earnMaxAmt;
    private String earnEtcCn;

    private String mrgSttsCd;
    private String plcyMajorCd;
    private String schoolCd;
    private String jobCd;

    private String addAplyQlfcCndCn;
    private String ptcpPrpTrgtCn;

    private String inqCnt;

    private String frstRegDt;
    private String lastMdfcnDt;
}